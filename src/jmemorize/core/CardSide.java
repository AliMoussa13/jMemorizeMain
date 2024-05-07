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

import java.util.LinkedList;
import java.util.List;

/**
 * A card is made up of two card sides which can contain various contents, the
 * most important being text.
 * 
 * @author djemili
 */
public class CardSide implements Cloneable
{
    public interface CardSideObserver
    {
        public void onTextChanged(CardSide cardSide, FormattedText text);
        public void onImagesChanged(CardSide cardSide, List<String> imageIDs);
    }
    
    private FormattedText mText;
    private List<String> mImageIDs = new LinkedList<>();
    private List<CardSideObserver> mObservers = new LinkedList<>();
    
    public CardSide()
    {
    }
    
    public CardSide(FormattedText text)
    {
        setText(text);
    }
    
    public FormattedText getText()
    {
        return mText;
    }
    
    /**
     * Note that using this method won't modify the modification date of the
     * card. Use {@link Card#setSides(String, String)} instead for modifications 
     * done by the user.
     */
    public void setText(FormattedText text)
    {
        if (text.equals(mText))
            return;
        
        mText = text;
        
        for (CardSideObserver observer : mObservers)
        {
            observer.onTextChanged(this, mText);
        }
    }
    
    /**
     * @return the IDs of all images of this card side.
     */
    public List<String> getImages()
    {
        return mImageIDs;
    }
    
    public void setImages(List<String> ids)
    {
        if (mImageIDs.equals(ids))
            return;
        
        mImageIDs.clear();
        mImageIDs.addAll(ids);
        
        for (CardSideObserver observer : mObservers)
        {
            observer.onImagesChanged(this, mImageIDs);
        }
    }
    
    public void addObserver(CardSideObserver observer)
    {
        mObservers.add(observer);
    }
    
    public void removeObserver(CardSideObserver observer)
    {
        mObservers.remove(observer);
    }
    
    /** 
     * @return the unformatted string representation of the formatted text.
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        return mText.getUnformatted();
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    public Object clone() throws CloneNotSupportedException
    {
        super.clone();
        CardSide cardSide = new CardSide();
        cardSide.mText = (FormattedText) mText.clone();
        cardSide.mImageIDs.addAll(mImageIDs);
        
        return cardSide;
    }
}
