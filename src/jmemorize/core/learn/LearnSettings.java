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
package jmemorize.core.learn;

import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

import jmemorize.core.Main;
import jmemorize.gui.LC;
import jmemorize.gui.Localization;

/**
 * This class holds learn session settings which can be saved and loaded. This
 * class is used when creating a learn session and defines the strategy that
 * should be used while in that learn session. You can enable limits, schedules
 * and other customizations.
 *
 * @author djemili
 */
public class LearnSettings
{
    public enum SchedulePreset {CONST, LINEAR, QUAD, EXPONENTIAL, CRAM, CUSTOM}

    // schedules
    public static final int      SCHEDULE_LEVELS       = 10;
    public static final String[] SCHEDULE_PRESETS      = new String[] {
            Localization.get(LC.SCHEDULE_CONST),
            Localization.get(LC.SCHEDULE_LINEAR),
            Localization.get(LC.SCHEDULE_QUAD),
            Localization.get(LC.SCHEDULE_EXPONENTIAL),
            Localization.get(LC.SCHEDULE_CRAM),
            Localization.get(LC.SCHEDULE_CUSTOM)
    };

    // side mode enums
    public static final int      SIDES_NORMAL          = 0;
    public static final int      SIDES_FLIPPED         = 1;
    public static final int      SIDES_RANDOM          = 2;
    public static final int      SIDES_BOTH            = 3;

    // category order when grouping
    public static final int      CATEGORY_ORDER_FIXED  = 0;
    public static final int      CATEGORY_ORDER_RANDOM = 1;

    // Indicates the number of times that each side must be done correctly
    // before it is declared 'learned'
    private int mAmountToTestFront = 1;
    private int mAmountToTestBack = 1;

    private SchedulePreset mSchedulePreset;
    private int[] mSchedule;

    private boolean mFixedExpirationTimeEnabled;
    private int mFixedExpirationHour;
    private int mFixedExpirationMinute;

    private int mLimitTime;
    private boolean mRetestFailedCards;
    private int mSides;
    private boolean mGroupByCategory;
    private int mCategoryOrder;
    private float mShuffleRatio;

    private boolean mLimitCardsEnabled;
    private boolean mLimitTimeEnabled;
    private int mLimitCards;


    /**
     * Constructs a new learn settings object with default settings.
     */
    public LearnSettings()
    {
        setSchedulePreset(SchedulePreset.LINEAR);
    }

    /**
     * @return <code>true</code> if the card limit is enabled.
     */
    public boolean isCardLimitEnabled()
    {
        return mLimitCardsEnabled;
    }

    /**
     * Enables/disables the card limit.
     *
     * @param enabled <code>true</code> if the card limit should be enabled.
     * <code>false</code> otherwise.
     */
    public void setCardLimitEnabled(boolean enabled)
    {
        mLimitCardsEnabled = enabled;
    }

    /**
     * Sets the cards limit.
     *
     * @param limit the new card limit.
     */
    public void setCardLimit(int limit)
    {
        mLimitCards = limit;
    }

    /**
     * @return the card limit.
     */
    public int getCardLimit()
    {
        return mLimitCards;
    }

    /**
     * @return <code>true</code> if the time limit is enabled.
     */
    public boolean isTimeLimitEnabled()
    {
        return mLimitTimeEnabled;
    }

    /**
     * Enables/disables the time limit.
     *
     * @param enabled <code>true</code> if the time limit should be enabled.
     * <code>false</code> otherwise.
     */
    public void setTimeLimitEnabled(boolean enabled)
    {
        mLimitTimeEnabled = enabled;
    }

    /**
     * Sets the time limit.
     *
     * @param limit the time limit in minutes.
     */
    public void setTimeLimit(int limit)
    {
        mLimitTime = limit;
    }

    /**
     * @return the time limit in minutes.
     */
    public int getTimeLimit()
    {
        return mLimitTime;
    }

    /**
     * @param retest <code>true</code> if cards that have been failed while
     * learning should be put back into the list of cards to learn.
     * <code>false</code> if all cards should never be tested more then once
     * in a session.
     */
    public void setRetestFailedCards(boolean retest)
    {
        mRetestFailedCards = retest;
    }

    /**
     * @see LearnSettings#setRetestFailedCards(boolean)
     * @return <code>true</code> if failed cards can appear more then once in
     * a session.
     */
    public boolean isRetestFailedCards()
    {
        return mRetestFailedCards;
    }

    /**
     * Sets the new side mode. The possible side modes are:
     *
     * <ul>
     * <li>SIDES_NORMAL: show cards in regular front-to-flip mode.</li>
     * <li>SIDES_FLIPPED: show cards in flip-to-front mode.</li>
     * <li>SIDES_RANDOM: show cards with randomly selected NORMAL or FLIPPED
     * sides mode.</li>
     * <li>SIDES_BOTH: show both sides of cards as specified in
     * {@link #setAmountToTest(boolean, int)}.</li>
     * </ul>
     *
     * @param mode either SIDES_NORMAL, SIDES_FLIPPED, SIDES_RANDOM or
     * SIDES_BOTH;
     */
    public void setSidesMode(int mode)
    {
        mSides = mode;
    }

    /**
     * @return The current sides mode as given by enum SIDES_NORMAL,
     * SIDES_FLIPPED and SIDES_RANDOM.
     */
    public int getSidesMode()
    {
        return mSides;
    }

    /**
     * @param frontside True if you want to get the amount to test the front
     * @return The amount you need to test a given side before it is declared
     * learnt
     */
    public int getAmountToTest(boolean frontside)
    {
        if (frontside)
            return mAmountToTestFront;
        else
            return mAmountToTestBack;
    }

    /**
     * @param frontside True if you want to set the amount to test the front
     * @param value The number of correct consecutive tests before it is
     * declared learnt
     */
    public void setAmountToTest(boolean frontside, int value)
    {
        if (frontside)
            mAmountToTestFront = value;
        else
            mAmountToTestBack = value;
    }

    /**
     * The schedule tells how much time should pass before a cards that has
     * moved into a higher deck level needs be rechecked.
     *
     * @param schedule A int array that holds the time span values that need to
     * pass and where the index is the deck level before the card moved - e.g.
     * <code>schedule[0] = 60</code> says that a card that has moved from deck
     * 0 to deck 1 should be rechecked in one hour. The time spans are given in
     * minutes.
     */
    public void setCustomSchedule(int[] schedule)
    {
        mSchedule = schedule;
        mSchedulePreset = SchedulePreset.CUSTOM;
    }

    /**
     * @see LearnSettings#//setSchedule(int[])
     * @return The current schedule.
     */
    public int[] getSchedule()
    {
        return mSchedule;
    }

    /**
     * Sets the schedule to one of the available schedule presets.
     *
     * @param// idx the index of the schedule preset. See
     * {@link #SCHEDULE_PRESETS}
     */
    public void setSchedulePreset(SchedulePreset preset)
    {
        mSchedule = getPresetSchedule(preset);
        mSchedulePreset = preset;
    }

    /**
     * @return the currently set schedule preset.
     */
    public SchedulePreset getSchedulePreset()
    {
        return mSchedulePreset;
    }

    /**
     * @param hour needs to be given in 24-hour format.
     */
    public void setFixedExpirationTime(int hour, int minute)
    {
        mFixedExpirationHour = hour;
        mFixedExpirationMinute = minute;
    }

    public int getFixedExpirationHour()
    {
        return mFixedExpirationHour;
    }

    public int getFixedExpirationMinute()
    {
        return mFixedExpirationMinute;
    }

    public void setFixedExpirationTimeEnabled(boolean enable)
    {
        mFixedExpirationTimeEnabled = enable;
    }

    public boolean isFixedExpirationTimeEnabled()
    {
        return mFixedExpirationTimeEnabled;
    }

    /**
     * @return the correct expiration date according to the current schedule
     * settings.
     *
     * @param learnDate The moment that the card is learned.
     * @param// currentLavel The deck level of the card before raising it to the
     * next level.
     */
    public Date getExpirationDate(Date learnDate, int currentLevel)
    {
        int deckDelay = getSchedule()[Math.min(currentLevel, 9)];
        long millis = learnDate.getTime() + 60l * 1000l * deckDelay;

        Date date = new Date(millis);

        if (mFixedExpirationTimeEnabled)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            // if due time already passed today
            if (hour > mFixedExpirationHour ||
                    (hour == mFixedExpirationHour && minute >= mFixedExpirationMinute))
            {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            cal.set(Calendar.HOUR_OF_DAY, mFixedExpirationHour);
            cal.set(Calendar.MINUTE, mFixedExpirationMinute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            date = cal.getTime();
        }

        return date;
    }

    /**
     * Enables/disables grouping by categories.
     *
     * @param enable <code>true</code> if grouping should be enabled.
     * <code>false</code> otherwise.
     */
    public void setGroupByCategory(boolean enable)
    {
        mGroupByCategory = enable;
    }

    /**
     * @return <code>true</code> if cards should be grouped by categories.
     */
    public boolean isGroupByCategory()
    {
        return mGroupByCategory;
    }

    /**
     * This method sets the order by which categories will be shown when
     * grouping by categories is enabled.
     *
     * @param order Is either CATEGORY_ORDER_CARDS or CATEGORY_ORDER_FIXED.
     */
    public void setCategoryOrder(int order)
    {
        mCategoryOrder = order;
    }

    /**
     * @return either CATEGORY_ORDER_CARDS or CATEGORY_ORDER_FIXED
     */
    public int getCategoryOrder()
    {
        return mCategoryOrder;
    }

    /**
     * 0.0f means that all cards appear in the order of their level. 1.0f means
     * that all cards appear in totally random order.
     *
     * Something in between denotes the share of cards that will be learned at a
     * random level value.
     */
    public void setShuffleRatio(float ratio)
    {
        mShuffleRatio = ratio;
    }

    /**
     * The share of cards that will appear at a random level in the learn
     * session.
     */
    public float getShuffleRatio()
    {
        return mShuffleRatio;
    }

    /**
     * Gets one of the preset schedules.
     *
     * @param //idx the index of the preset schedule. See
     * {@link #SCHEDULE_PRESETS}
     * @return one of the preset schedule.
     */
    public static int[] getPresetSchedule(SchedulePreset preset)
    {
        int schedule[] = new int[SCHEDULE_LEVELS];
        int presetIndex = preset.ordinal();

        if (presetIndex < 0 || presetIndex > 4)
        {
            Logger log = Main.getLogger();
            log.warning("Preset schedule with this index not found."); //$NON-NLS-1$

            presetIndex = 1;
        }

        switch (presetIndex)
        {
            case 0 : //constant
                for (int i = 0; i < SCHEDULE_LEVELS; i++)
                {
                    schedule[i] = 60 * 24;
                }
                return schedule;

            case 1 : //linear
                for (int i = 0; i < SCHEDULE_LEVELS; i++)
                {
                    schedule[i] = (i+1) * 60 * 24;
                }
                return schedule;

            case 2 : //quadratic
                for (int i = 0; i < SCHEDULE_LEVELS; i++)
                {
                    schedule[i] = (int)Math.pow((double)i+1, 2) * 60 * 24;
                }
                return schedule;

            case 3 : //exponential
                for (int i = 0; i < SCHEDULE_LEVELS; i++)
                {
                    schedule[i] = (int)Math.pow(2, i) * 60 * 24;
                }
                return schedule;

            case 4 : //cram
            default:
                for (int i = 0; i < SCHEDULE_LEVELS; i++)
                {
                    schedule[i] = (i+1) * 5;
                }
                return schedule;
        }
    }
}