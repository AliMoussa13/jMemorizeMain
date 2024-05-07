/*
 * jMemorize - Learning made easy (and fun) - A Leitner flashcards tool
 * Copyright(C) 2004-2008 Riad Djemili and contributors
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
package jmemorize.core;

import java.util.Date;
import java.util.List;

import jmemorize.core.CardSide.CardSideObserver;

/**
 * A flash card that has a front/flip side and can be learned.
 * 
 * @author djemili
 * @version $Id: Card.java 1048 2008-01-21 21:40:00Z djemili $
 */
public class Card implements Events, Cloneable
{
    public static final long    ONE_DAY     = 1000 * 60 * 60 * (long)24;
    public static final boolean CLONE_DATES = Main.isDevel();

    private Category mCategory;
    private int mLevel;

    // content
    private CardSide mFrontSide = new CardSide();
    private CardSide mBackSide = new CardSide();
    
    // dates
    private Date mDateTested;
    private Date mDateExpired;
    private Date mDateCreated;
    private Date mDateModified;
    private Date mDateTouched; //this date is used internaly to order cards

    // stats
    private int mTestsTotal;
    private int mTestsHit;    //succesfull learn repetitions
    private int mFrontHitsCorrect;
    private int mBackHitsCorrect;

    /**
     * Assumes formatted front- and backsides
     */
    public Card(String front, String back)
    {
        this(FormattedText.formatted(front), FormattedText.formatted(back));
    }
    
    public Card(FormattedText front, FormattedText back) 
    {
        this(new Date(), front, back);
    }
    
    /**
     * The card sides are given in a formatted state.
     */
    public Card(Date created, String front, String back)
    {
        this(created, FormattedText.formatted(front), FormattedText.formatted(back));
    }
    
    public Card(Date created, FormattedText front, FormattedText back)
    {
        this(created, new CardSide(front), new CardSide(back));
    }
    
    public Card(Date created, CardSide frontSide, CardSide backSide)
    {
        mDateCreated = cloneDate(created);
        mDateModified = cloneDate(created);
        mDateTouched = cloneDate(created);

        mFrontSide = frontSide;
        mBackSide = backSide;
        
        attachCardSideObservers();
    }

    /**
     * The given card sides are assumend to be unformatted.
     * 
     * @throws IllegalArgumentException If frontSide or backSide has no text.
     */
    public void setSides(String front, String back)
    {
        FormattedText frontSide = FormattedText.unformatted(front);
        FormattedText backSide = FormattedText.unformatted(back);
        
        setSides(frontSide, backSide);
    }
    
    /**
     * @throws IllegalArgumentException If front or back has no text.
     */
    public void setSides(FormattedText front, FormattedText back) 
        throws IllegalArgumentException
    {
        if (front.equals(mFrontSide.getText()) &&
            back.equals(mBackSide.getText()))
        {
            return;
        }
        
        mFrontSide.setText(front);
        mBackSide.setText(back);
        
        if (mCategory != null)
        {
            mDateModified = new Date();
            mCategory.fireCardEvent(EDITED_EVENT, this, getCategory(), mLevel);
        }
    }
    
    /**
     * Get the number of times a specific card side was already learned in its
     * deck.
     * 
     * @param frontside <code>true</code> if it should deliver the fronside
     * value, <code>false</code> if it should deliver the backside value.
     * 
     * @return the amount of times that the specified side was learned in this
     * deck.
     */
    public int getLearnedAmount(boolean frontside)
    {
        // TODO move to CardSide class
        
        return frontside ? mFrontHitsCorrect : mBackHitsCorrect;
    }

    /**
     * Set the number of times a specific card side was already learned in its
     * deck.
     * 
     * @param frontside <code>true</code> if it should deliver the fronside
     * value, <code>false</code> if it should deliver the backside value.
     * 
     * @param amount the amount of times that the specified side was learned in
     * this deck.
     */
    public void setLearnedAmount(boolean frontside, int amount)
    {
//      TODO move to CardSide class
        
        if (frontside)
        {
            mFrontHitsCorrect = amount;
        }
        else
        {
            mBackHitsCorrect = amount;
        }

        if (mCategory != null)
        {
            mCategory.fireCardEvent(DECK_EVENT, this, getCategory(), mLevel);
        }
    }

    /**
     * Increment the number of times a specific card side was already learned in
     * its deck by one.
     * 
     * @param frontside <code>true</code> if it should deliver the fronside
     * value, <code>false</code> if it should deliver the backside value.
     */
    public void incrementLearnedAmount(boolean frontside)
    {
//      TODO move to CardSide class
        
        setLearnedAmount(frontside, getLearnedAmount(frontside) + 1);
    }

    /**
     * Resets the amount of times that the card sides were learned in this deck
     * to 0.
     */
    public void resetLearnedAmount()
    {
        setLearnedAmount(true, 0);
        setLearnedAmount(false, 0);
    }

    public CardSide getFrontSide()
    {
        return mFrontSide;
    }

    public CardSide getBackSide()
    {
        return mBackSide;
    }
    
    /**
     * @return the date that this card appeared the last time in a test and was
     * either passed or failed (skip doesn't count).
     */
    public Date getDateTested()
    {
        return cloneDate(mDateTested);
    }

    public void setDateTested(Date date)
    {
        mDateTested = cloneDate(date);
        mDateTouched = cloneDate(date);
    }

    /**
     * @return can be <code>null</code>.
     */
    public Date getDateExpired()
    {
        return cloneDate(mDateExpired);
    }

    /**
     * @param date can be <code>null</code>.
     */
    public void setDateExpired(Date date) // CHECK should this throw a event?
    {
        mDateExpired = cloneDate(date);
    }

    /**
     * @return the creation date. Is never <code>null</code>.
     */
    public Date getDateCreated()
    {
        return cloneDate(mDateCreated);
    }

    public void setDateCreated(Date date)
    {
        if (date == null) 
            throw new NullPointerException();
        
        mDateCreated = cloneDate(date);
    }
    
    /**
     * @return the modification date. Is never <code>null</code>.
     */
    public Date getDateModified()
    {
        return mDateModified;
    }

    /**
     * @param date must be equal or after the creation date.
     */
    public void setDateModified(Date date)
    {
        if (date.before(mDateCreated))
            throw new IllegalArgumentException(
                "Modification date can't be before creation date.");
        
        mDateModified = date;
    }

    /**
     * @return DateTouched is the date that this card was learned, skipped,
     * reset or created the last time. This value is used to sort cards by a
     * global value that is unique for all categories and decks.
     */
    public Date getDateTouched()
    {
        return cloneDate(mDateTouched);
    }

    public void setDateTouched(Date date)
    {
        mDateTouched = cloneDate(date);
    }

    /**
     * @return Number of times this card has been tested.
     */
    public int getTestsTotal()
    {
        return mTestsTotal;
    }

    /**
     * @return Number of times this card has been tested succesfully.
     */
    public int getTestsPassed()
    {
        return mTestsHit;
    }

    /**
     * @return The percentage of times that this card has passed learn tests in
     * comparison to failed tests.
     */
    public int getPassRatio()
    {
        return (int)Math.round(100.0 * mTestsHit / mTestsTotal);
    }

    public void incStats(int hit, int total)
    {
        mTestsTotal += total;
        mTestsHit += hit;
    }

    public void resetStats()
    {
        mTestsTotal = 0;
        mTestsHit = 0;
        
        mFrontHitsCorrect = 0;
        mBackHitsCorrect = 0;
    }

    public Category getCategory()
    {
        return mCategory;
    }

    protected void setCategory(Category category)
    {
        mCategory = category;
    }

    /**
     * A card is expired when it was learned/repeated succesfully, but its learn
     * time has expired (is in the past from current perspective).
     * 
     * @return True if the card has expired.
     */
    public boolean isExpired()
    {
        Date now = Main.getNow();
        return mDateExpired != null &&
            (mDateExpired.before(now) || mDateExpired.equals(now));
    }

    /**
     * A card is learned when it was learned/repeated succesfully and its learn 
     * time hasnt expired.
     * 
     * @return True if the card is learned.
     */
    public boolean isLearned()
    {
        return mDateExpired != null && mDateExpired.after(Main.getNow());
    }

    /**
     * A card is unlearned when it wasnt succesfully repeated or never been l
     * earned at all.
     * 
     * @return True if the card is unlearned.
     */
    public boolean isUnlearned()
    {
        return mDateExpired == null;
    }

    /**
     * @return Returns the level.
     */
    public int getLevel()
    {
        return mLevel;
    }

    /**
     * @param level The level to set.
     */
    protected void setLevel(int level)
    {
        mLevel = level;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    public Object clone()
    {
        Card card = null;
        try
        {
            card = (Card)super.clone();
            card.mFrontSide = (CardSide) mFrontSide.clone();
            card.mBackSide = (CardSide) mBackSide.clone();
            
            card.mDateCreated = cloneDate(mDateCreated);
            card.mDateExpired = cloneDate(mDateExpired);
            card.mDateModified = cloneDate(mDateModified);
            card.mDateTested = cloneDate(mDateTested);
            card.mDateTouched = cloneDate(mDateTouched);
            
            card.mCategory = null; // don't clone category
        }
        catch (CloneNotSupportedException e) 
        {
            assert false;
        }
        
        return card;
    }
    
    /**
     * Clones the card without copying its user-dependent progress stats.
     * 
     * This includes the following data: Fronside, Flipside, Creation date.
     * Setting the right category needs to be handled from the out side.
     */
    public Card cloneWithoutProgress()
    {
        try
        {
            return new Card(mDateCreated,
                (CardSide) mFrontSide.clone(), (CardSide) mBackSide.clone());
        }
        catch (CloneNotSupportedException e)
        {
            assert false;
            return null; // satisfy compiler
        }
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        return "("+ mFrontSide +"/"+ mBackSide +")";
    }
    
    private void attachCardSideObservers()
    {
        CardSideObserver observer = new CardSideObserver() {
            public void onImagesChanged(CardSide cardSide, List<String> imageIDs)
            {
                if (mCategory != null)
                {
                    mDateModified = new Date();
                    mCategory.fireCardEvent(EDITED_EVENT, Card.this, getCategory(), mLevel);
                }
            }

            public void onTextChanged(CardSide cardSide, FormattedText text)
            {
                // already handled by set sides
                // TODO handle event notfying here
            }
        };
        
        mFrontSide.addObserver(observer);
        mBackSide.addObserver(observer);
    }

    /**
     * @return clone of given date or <code>null</code> if given date was
     * <code>null</code>.
     */
    private Date cloneDate(Date date)
    {
        if (CLONE_DATES)
        {
            return date == null ? null : (Date)date.clone();
        }
        
        return date;
    }
}
