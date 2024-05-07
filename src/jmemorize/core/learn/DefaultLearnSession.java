/*
 * jMemorize - Learning made easy (and fun) - A Leitner flashcards tool
 * Copyright(C) 2004-2008 Riad Djemili
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package jmemorize.core.learn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import jmemorize.core.Card;
import jmemorize.core.Category;
import jmemorize.core.CategoryObserver;
import jmemorize.util.EquivalenceClassSet;

/**
 * A learn session is instantiated with a LearnSettings object which defines the
 * rules which the session should handle cards.
 *
 * The workflow for this class is as following:
 *
 * <ol>
 * <li>Learn Session fetches a card according to its LearnSettings. To get
 * notified of this card, use the {@link LearnCardObserver}.</li>
 * <li>The learn session waits for a call to either {{@link #cardChecked(boolean,
 * boolean)} or {@link #cardSkipped()}. This makes the learn session perform some 
 * action on the card.</li>
 * <li>This action results in some category event, which the learn session gets
 * notified of, because it attached itself also as a category observer to the
 * category that is to be learned. This category event results in either
 * fetching the next card (see above) or all call to the
 * {@link LearnSessionProvider} to inform it that the session has ended. The
 * {@link LearnSessionProvider} then notifies all of its
 * {@link LearnSessionObserver}.</li>
 * </ol>
 *
 * Note that when a card is neither learned or skipped, but i.e. deleted or
 * resetted, step 2 is skipped and step 3 comes into play directly.
 *
 * The order of a card in the learn session depends on the shuffle and category order settings:
 * [Shuffle: Off, Category Order: Off] Deck, Last test date
 * [Shuffle: Off, Category Order: On ] Category, Deck, Last test date
 * [Shuffle: On,  Category Order: Off] Deck, Random number
 * [Shuffle: On,  Category Order: On ] Category, Deck, Random number
 *
 * @author djemili
 */
public class DefaultLearnSession implements CategoryObserver, LearnSession
{
    /**
     * A Comparator that is used for sorting cards in learn sessions.
     * This is used to sort the cards into equivalence classes, from
     * which the next card will be drawn randomly.
     */
    private class CardComparator implements Comparator<CardInfo>
    {
        private Map<Category, Integer> m_categoryGroupOrder;

        public CardComparator(Map<Category, Integer> categoryGroupOrder)
        {
            m_categoryGroupOrder = categoryGroupOrder;
        }

        /*
         * @see java.util.Comparator
         */
        public int compare(CardInfo card0, CardInfo card1)
        {
            if (card0.getLevel() < card1.getLevel() )
            {
                return -1;
            }
            else if (card0.getLevel() > card1.getLevel() )
            {
                return 1;
            }

            if (mSettings.isGroupByCategory())
            {
                Integer cat0 = m_categoryGroupOrder.get(card0.getCategory());
                Integer cat1 = m_categoryGroupOrder.get(card1.getCategory());

                if (cat0 != null && cat1 != null)
                {
                    if (cat0.intValue() <  cat1.intValue())
                    {
                        return -1;
                    }
                    else if (cat0.intValue() > cat1.intValue())
                    {
                        return 1;
                    }
                }

            }

            return 0;
        }
    }

    /**
     * This class is a wrapper for a card. It allows to associate additional
     * data to a card, that is only relevant during a single specifc learn
     * session.
     */
    private class CardInfo
    {
        private Card mCard;

        /**
         * For learning this variable should be used instead of the real level
         * of the card. This allows for some special shuffling techniques.
         */
        private int mLevel;

        public CardInfo(Card card)
        {
            mCard = card;
            mLevel = card.getLevel();
        }

        public Card getCard()
        {
            return mCard;
        }

        public int getLevel()
        {
            return mLevel;
        }

        public void setLevel(int level)
        {
            mLevel = level;
        }

        public Category getCategory()
        {
            return mCard.getCategory();
        }

        @Override
        public String toString()
        {
            return "CardInfo("+ mCard.toString()+")";
        }
    }

    // learn session settings
    private Category mCategory;

    // the root category of the lesson
    private Category mRootCategory;
    private LearnSettings mSettings;
    private LearnSessionProvider mProvider;

    // current learn session state
    private boolean mQuit;
    private boolean mLearningStarted = false;
    private CardInfo mCurrentCardInfo;

    // The cards in the set are partitioned into the following exclusive
    // subsets. Cards move from reserve to active, from active to reserve or
    // learned, but do not move after reaching learned.
    private EquivalenceClassSet<CardInfo> mCardsActive;
    private EquivalenceClassSet<CardInfo> mCardsReserve;

    // the list of all cards that have been checked in the order last seen. Does
    // not include cards that were skipped and never passed/failed.
    private List<Card> mCardsChecked = new ArrayList<>();
    private Set<Card> mCardsLearned = new HashSet<>();
    private Map<Card, CardInfo> mCardsInfoMap = new HashMap<>();

    // NOTE - m_cardsLearned is the set of all cards successfully learned
    // this session, which is the union of "passed" and "relearned".
    // We don't track of those two categories internally.
    // "Passed" = Learned - EverFailed
    // "ReLearned" = Learned intersect EverFailed
    // "Failed" = EverFailed - Learned

    // These sets are non exclusive markers that indicate the status of a card
    // Note that these are Sets not Lists.  Two reasons:
    //  1)  The order is not important.
    //  2)  Lookup efficiency is better.

    // Cards do not get removed from the EverFailed list.
    private Set<Card> mCardsEverFailed = new HashSet<>();
    private Set<Card> mCardsSkipped = new HashSet<>();

    // NOTE - this is only the *active* cards which are partially learned -
    // there may be others in the reserve set.
    private Set<Card> mCardsActivePartiallyLearned = new HashSet<>();

    // Further invariants:
    //   - Learned intsersection Skipped = NULL
    //   - partialPassed intersection Learned = NULL

    // etc
    private Random mRand = new Random();
    private List<LearnCardObserver> mCardObservers = new LinkedList<>();

    private Date mStart;
    private Date mEnd;

    private Logger mLogger = Logger.getLogger("jmemorize.session");

    /**
     * Creates a new learn session. Use {@link #startLearning()} to start the
     * learning.
     */
    public DefaultLearnSession(Category category,
                               LearnSettings settings, List<Card> selectedCards,
                               boolean learnUnlearned, boolean learnExpired,
                               LearnSessionProvider provider)
    {
        mRootCategory = category;
        while (mRootCategory.getParent() != null)
            mRootCategory = mRootCategory.getParent();

        mCategory = category;
        mRootCategory.addObserver(this);

        mSettings = settings;
        mProvider = provider;

        setupLogger();

        Map<Category, Integer> order = mSettings.isGroupByCategory() ?
                createCategoryGroupOrder() : null;

        mCardsActive = fetchCards(selectedCards, learnUnlearned, learnExpired, order);
        mCardsReserve = new EquivalenceClassSet<CardInfo>(mCardsActive.getComparator());
        // Note that EquivalenceClassSets always default to shuffle mode (any card
        // from the current class may be chosen next.)  This is what we want here.
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void startLearning()
    {
        if (mLearningStarted)
            throw new IllegalStateException("startLearning should only happen once!");

        mLearningStarted = true;
        mStart = new Date();

        // move all cards to cardsPastLimit, then fetch exactly as many as needed
        if (mSettings.isCardLimitEnabled() &&
                mCardsActive.size() > mSettings.getCardLimit())
        {
            mCardsReserve = mCardsActive;
            mCardsActive = mCardsReserve.partition(mSettings.getCardLimit());
        }

        gotoNextCard();
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void endLearning()
    {
        mEnd = new Date();

        mRootCategory.removeObserver(this);
        mProvider.sessionEnded(this);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public LearnSettings getSettings()
    {
        return mSettings;
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Date getStart()
    {
        return mStart;
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Date getEnd()
    {
        return mEnd;
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Card getCurrentCard()
    {
        return mCurrentCardInfo.getCard();
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Set<Card> getCardsLeft()
    {
        return Collections.unmodifiableSet(toCardSet(mCardsActive));
    }

    public int getNCardsPartiallyLearned()
    {
        return mCardsActivePartiallyLearned.size();
    }

    public int getNCardsLearned()
    {
        return mCardsLearned.size();
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Category getCategory()
    {
        return mCategory;
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void cardChecked(boolean passed, boolean shownFlipped)
    {
        Card currentCard = mCurrentCardInfo.getCard();

        mLogger.fine(String.format("cardChecked: %b %s",
                passed, currentCard.getFrontSide().getText()));

        assert !mCardsLearned.contains(currentCard);
        assert !mCardsReserve.contains(mCurrentCardInfo);
        assert mCardsActive.contains(mCurrentCardInfo);

        mCardsSkipped.remove(currentCard);
        mCardsActivePartiallyLearned.remove(currentCard);

        if (passed)
        {
            boolean raiseLevel = true;

            // If we are using the 'Check both sides' strategy, we need to do
            // different calculations to work out whether the card is ready to
            // be raised a level
            if (mSettings.getSidesMode() == LearnSettings.SIDES_BOTH)
            {
                // Work out how much of it is learned
                int frontAmountLearned = currentCard.getLearnedAmount(true);
                int backAmountLearned = currentCard.getLearnedAmount(false);

                if (shownFlipped)
                    backAmountLearned++;
                else
                    frontAmountLearned++;

                if ((frontAmountLearned < mSettings.getAmountToTest(true))
                        || (backAmountLearned < mSettings.getAmountToTest(false)))
                {
                    // It's partially learned.
                    //  increment the amount it has been learned by
                    mCardsActivePartiallyLearned.add(currentCard);
                    mLogger.fine("...partially passed.");
                    raiseLevel = false;

                    // incremenLearnedAmount fires a DECK_EVENT
                    currentCard.incrementLearnedAmount(!shownFlipped);
                }
            }

            if (raiseLevel)
            {
                mLogger.fine("...passed.");
                raiseCardLevel(currentCard);
            }
        }
        else
        {
            // TODO should this be renamed since currently only cards with
            // level > 0 are called failed in session summaries
            if (!mSettings.isRetestFailedCards())
                mCardsActive.remove(mCurrentCardInfo);

            if (currentCard.getLevel() > 0)
            {
                mCardsEverFailed.add(currentCard);
                mLogger.fine("...failed.");
            }

            /* NOTE - If the card is still active, the card may be in the wrong
             * equivalence class (i.e. the set is in an inconsistent state).
             * We can't fix it until after the reset, *but* the
             * resetCardLevel() method fires the event which results in the
             * observers reacting (checking for end of session, getting the
             * next card, etc).  The card's equivalence class will be wrong,
             * but this should not be a problem for gotoNextCard.
             * We reset the equivalence class as soon as possible.
             */
            Category.resetCardLevel(currentCard, mStart);

            mCurrentCardInfo.setLevel(currentCard.getLevel());
            mCardsActive.resetEquivalenceClass(mCurrentCardInfo);
        }

        mLogger.fine("...Cards remaining: " + mCardsActive.size());
        mLogger.fine("...Cards partially learned: " + getNCardsPartiallyLearned());
        mLogger.fine("...num failed= " + mCardsEverFailed.size());

        // note that raising/reseting card level will be noticed by onCardEvent.
        // program flow continues there.
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void cardSkipped()
    {
        Card currentCard = mCurrentCardInfo.getCard();

        // Note that we do not remove the card from m_cardsChecked.
        mLogger.fine("cardSkipped: " + currentCard.getFrontSide());

        assert !mCardsLearned.contains(currentCard);
        assert !mCardsReserve.contains(mCurrentCardInfo);
        assert mCardsActive.contains(mCurrentCardInfo);

        mCardsSkipped.add(currentCard);

        if (mCardsReserve != null && mCardsReserve.size() > 0)
        {
            mCardsActivePartiallyLearned.remove(mCurrentCardInfo);

            CardInfo replacementCardInfo = mCardsReserve.loopIterator().next();
            Card replacementCard = replacementCardInfo.getCard();

            if (replacementCard.getLearnedAmount(true) > 0 ||
                    replacementCard.getLearnedAmount(false) > 0)
            {
                mCardsActivePartiallyLearned.add(replacementCard);
            }

            mCardsActive.add(replacementCardInfo);
            mCardsReserve.remove(replacementCardInfo);
            mCardsReserve.addExpired(mCurrentCardInfo);
            mCardsActive.remove(mCurrentCardInfo);

            mLogger.fine("Moving to reserve: " + currentCard.getFrontSide());
            mLogger.fine("Moving to active: " + replacementCard.getFrontSide());
        }

        mLogger.fine("...cards remaining: " + mCardsActive.size());

        Category.reappendCard(currentCard);

        // program flow continues in onCardEvent
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Set<Card> getPassedCards()
    {
        // "passed" = Learned and not Failed
        Set<Card> tempSet = new HashSet<>(mCardsLearned);
        tempSet.removeAll(mCardsEverFailed);
        return Collections.unmodifiableSet(tempSet);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Set<Card> getFailedCards()
    {
        Set<Card> tempSet = new HashSet<>(mCardsEverFailed);
        tempSet.removeAll(mCardsLearned);
        return Collections.unmodifiableSet(tempSet);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Set<Card> getSkippedCards()
    {
        return Collections.unmodifiableSet(mCardsSkipped);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public Set<Card> getRelearnedCards()
    {
        Set<Card> tempSet = new HashSet<>(mCardsEverFailed);
        tempSet.retainAll(mCardsLearned);
        return Collections.unmodifiableSet(tempSet);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void onTimer()
    {
        mQuit = true;
    }

    /* (non-Javadoc)
     * @see jmemorize.core.CategoryObserver
     */
    public void onCardEvent(int type, Card card, Category category, int deck)
    {
        CardInfo cardInfo = getCardInfo(card);

        if (cardInfo == null) // this happens when a new card is created; ignore
            return;

        switch (type)
        {
            case ADDED_EVENT:
                // if there is a reserve and we have enough cards, add to the reserve
                int allCards = mCardsLearned.size() + mCardsActive.size();
                if (mSettings.isCardLimitEnabled() && allCards >= mSettings.getCardLimit())
                {
                    mCardsReserve.add(cardInfo);
                }
                else
                {
                    mCardsActive.add(cardInfo);
                }
                break;

            case REMOVED_EVENT:
                // remove it from all sets
                mCardsActive.remove(cardInfo);
                mCardsReserve.remove(cardInfo);
                mCardsLearned.remove(card);
                mCardsActivePartiallyLearned.remove(card);
                mCardsEverFailed.remove(card);
                mCardsSkipped.remove(card);

                if (cardInfo == mCurrentCardInfo)
                {
                    gotoNextCard();
                }

                mCardsChecked.remove(card);
                break;

            case DECK_EVENT:
                if (cardInfo == mCurrentCardInfo)
                {
                    gotoNextCard();
                }

                // TODO currently, resetting a learned card does not put it back in the
                // active set.  Should it?
                break;
        }
    }

    /* (non-Javadoc)
     * @see jmemorize.core.CategoryObserver
     */
    public void onCategoryEvent(int type, Category category)
    {
        // no category events should occure while learning.
        // ignore
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public List<Card> getCheckedCards()
    {
        // TODO the meaning of this collides with the naming of checkCard(..)
        // because it also includes skipped cards
        return Collections.unmodifiableList(mCardsChecked);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public boolean isRelevant()
    {
        return mCardsEverFailed.size() > 0 || mCardsLearned.size() > 0;
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void addObserver(LearnCardObserver observer)
    {
        mCardObservers.add(observer);
    }

    /* (non-Javadoc)
     * @see jmemorize.core.LearnSession
     */
    public void removeObserver(LearnCardObserver observer)
    {
        mCardObservers.remove(observer);
    }

    /**
     * Note that this method is specialy for DefaultLearnSession and not part of
     * the LearnSession interface.
     *
     * @return the shuffled 'fake' card level that is currently used for the
     * card.
     */
    public int getCurrentShuffleLevel()
    {
        return mCurrentCardInfo.getLevel();
    }

    public boolean isQuit()
    {
        boolean noCardsLeft = mCardsActive.isEmpty();
        boolean limitReached = mSettings.isCardLimitEnabled() &&
                mCardsLearned.size() >= mSettings.getCardLimit();

        return mQuit || noCardsLeft || limitReached;
    }

    private void raiseCardLevel(Card card)
    {
        CardInfo cardInfo = getCardInfo(card);

        assert cardInfo != null;

        mCardsActive.remove(cardInfo);
        mCardsLearned.add(card);

        int level = card.getLevel();
        Date expiration = mSettings.getExpirationDate(mStart, level);
        Category.raiseCardLevel(card, mStart, expiration);
    }

    private void gotoNextCard()
    {
        // check for end condition
        if (isQuit())
        {
            endLearning();
        }
        else
        {
            CardInfo lastCardInfo = mCurrentCardInfo;

            mCurrentCardInfo = mCardsActive.loopIterator().next();

            // prevent the same card from occuring twice in a row
            if (mCardsActive.size() > 1 && lastCardInfo == mCurrentCardInfo)
            {
                mCurrentCardInfo = mCardsActive.loopIterator().next();
            }

            // add the new card to the checked list now so it can be edited as part of the set.
            // m_cardsChecked is ordered by last viewing, so remove prior to add
            Card currentCard = mCurrentCardInfo.getCard();

            mCardsChecked.remove(currentCard);
            mCardsChecked.add(currentCard);

            boolean flippedMode = checkIfFlipped();
            for (LearnCardObserver observer : mCardObservers)
            {
                observer.nextCardFetched(currentCard, flippedMode);
            }
        }
    }

    /**
     * Checks whether the card should be displayed as flipped or not.
     *
     * @return <code>true</code> if the card should be flipped.
     * <code>false</code> otherwise.
     */
    private boolean checkIfFlipped()
    {
        if (mSettings.getSidesMode() == LearnSettings.SIDES_RANDOM)
        {
            return mRand.nextInt(2) == 1; // 50% chance
        }
        else if (mSettings.getSidesMode() == LearnSettings.SIDES_BOTH)
        {
            // allocate the side proportionally to the amount they have left to learn
            Card currentCard = mCurrentCardInfo.getCard();

            int timesToLearnFront =
                    mSettings.getAmountToTest(true) -
                            currentCard.getLearnedAmount(true);

            int timesToLearnBack =
                    mSettings.getAmountToTest(false) -
                            currentCard.getLearnedAmount(false);

            if (timesToLearnBack < 0)
                timesToLearnBack = 0;

            if (timesToLearnFront < 0)
                timesToLearnFront = 0;

            if (timesToLearnFront + timesToLearnBack == 0)
                return false;

            int rand = mRand.nextInt(timesToLearnFront + timesToLearnBack);
            return rand < timesToLearnBack;
        }
        else
        {
            return (mSettings.getSidesMode() == LearnSettings.SIDES_FLIPPED);
        }
    }

    /**
     * Fetch the cards that should be learned in this session according to given
     * params.
     */
    private EquivalenceClassSet<CardInfo> fetchCards(List<Card> selectedCards,
                                                     boolean learnUnlearnedCards, boolean learnExpiredCards,
                                                     Map<Category, Integer> categoryGroupOrder)
    {
        List<Card> cards = new ArrayList<>();

        if (learnUnlearnedCards)
            cards.addAll(mCategory.getUnlearnedCards());

        if (learnExpiredCards)
            cards.addAll(mCategory.getExpiredCards());

        if (!learnUnlearnedCards && !learnExpiredCards)
            cards.addAll(selectedCards);


        List<Integer> levels = new LinkedList<>();
        List<CardInfo> cardInfos = new ArrayList<>(cards.size());
        mCardsInfoMap.clear();

        for (Card card : cards)
        {
            CardInfo cardInfo = new CardInfo(card);
            cardInfos.add(cardInfo);

            mCardsInfoMap.put(card, cardInfo);

            if (!levels.contains(card.getLevel()))
                levels.add(card.getLevel());
        }

        // shuffle random cards
        float shuffleRatio = mSettings.getShuffleRatio();
        int shuffledCardsCount = (int)(shuffleRatio * cards.size());


        List<CardInfo> shuffledCardInfos = new ArrayList<>(shuffledCardsCount);
        if (levels.size() > 1)
        {
            for (int i = 0; i < shuffledCardsCount; i++)
            {
                int randIndex = mRand.nextInt(cardInfos.size());

                CardInfo cardInfo = cardInfos.remove(randIndex);
                shuffledCardInfos.add(cardInfo);

                // randomly find a new level, which ISN'T our current level
                int randLevel = mRand.nextInt(levels.size() - 1);

                if (randLevel >= cardInfo.getLevel())
                    randLevel++;

                cardInfo.setLevel(levels.get(randLevel));
            }
        }

        // create equivalence set
        EquivalenceClassSet<CardInfo> cardSet =
                new EquivalenceClassSet<>(new CardComparator(categoryGroupOrder));

        cardSet.addAll(cardInfos);
        cardSet.addAll(shuffledCardInfos);

        return cardSet;
    }

    private Set<Card> toCardSet(Collection<CardInfo> cardInfos)
    {
        HashSet<Card> set = new HashSet<>();
        for (CardInfo cardInfo : cardInfos)
        {
            set.add(cardInfo.getCard());
        }

        return set;
    }

    private CardInfo getCardInfo(Card card)
    {
        return mCardsInfoMap.get(card);
    }

    /**
     * @return Cards that are being learned can be grouped by categories. In
     * this case the map holds for every category the position when it should
     * appear.
     */
    private Map<Category, Integer> createCategoryGroupOrder()
    {
        List<Category> categories = mCategory.getSubtreeList();

        if (mSettings.getCategoryOrder() == LearnSettings.CATEGORY_ORDER_RANDOM)
        {
            Collections.shuffle(categories);
        }

        HashMap<Category, Integer> map = new HashMap<>();
        int i = 0;
        for (Category category : categories)
        {
            map.put(category, i++);
        }
        // cards that have no category will be last in order
        map.put(null, i);

        return map;
    }

    private void setupLogger()
    {
        // TODO move to main?
        mLogger.setLevel(Level.FINE);
        Handler ch = new ConsoleHandler();
        ch.setLevel(Level.WARNING);
        Logger.getLogger("").addHandler(ch);
    }
}