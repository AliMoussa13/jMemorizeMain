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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;

public class ImageRepository
{
    private static final int MAX_CACHED_IMAGES = 10;

    public static final String IMG_ID_PREFIX = "::";    
    
    private static ImageRepository mInstance;

    private Map<String, ImageItem> mImageMap = new HashMap<>();
    private LinkedList<ImageIcon> mImageCache = new LinkedList<>();

    private static final Pattern   FILE_PATTERN = Pattern.compile("(.*)_(\\d+)");
    
    public class ImageItem
    {
        private String mSourceFile;
        private byte[] mBytes;
        private String mId;
 
        public ImageItem(InputStream in, String filename) 
            throws IOException
        {
            mSourceFile = filename;
            mId = createId(filename);
            mBytes = readFile(in);
        }
        
        public ImageIcon getImage()
        {
            ImageIcon image = new ImageIcon(mBytes);
            image.setDescription(IMG_ID_PREFIX + mId);
            
            return image;
        }

        public String getId()
        {
            return mId;
        }
        
        public String getFile()
        {
            return mSourceFile;
        }
        
        public byte[] getBytes()
        {
            return mBytes;
        }
        
        @Override
        public String toString()
        {
            return mId;
        }

        private String createId(String filename)
        {
            int dotPos = filename.lastIndexOf(".");
            String extension = filename.substring(dotPos);
            String purename = filename.substring(0, dotPos);
            
            while (getKeys().contains(purename + extension))
            {
                int num = 0;
                
                Matcher m = FILE_PATTERN.matcher(purename);
                if (m.matches() && m.groupCount() == 2)
                {
                    num = Integer.valueOf(m.group(2));
                    num++;
                    
                    purename = m.group(1);
                }

                purename = purename + "_" + num;
            }
            
            return purename + extension;
        }
        
        private byte[] readFile(InputStream in) throws IOException
        {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            
            byte[] bytes = new byte[1024];
            int numRead = 0;
            
            while ((numRead = in.read(bytes, 0, bytes.length)) >= 0)                
            {
                bytesOut.write(bytes, 0, numRead);
            }

            return bytesOut.toByteArray();
        }
    }
    
 // TODO remove singleton pattern and make this referenced from lesson
  
    public static ImageRepository getInstance() 
    {
        if (mInstance == null)
            mInstance = new ImageRepository();

        return mInstance;
    }
    
    public Set<String> getKeys()
    {
        return mImageMap.keySet();
    }
    
    public Collection<ImageItem> getImageItems() // TODO dont give imageItem to outside
    {
        return mImageMap.values();
    }
    
    public ImageIcon getImage(String imageId)
    {
        for (ImageIcon icon : mImageCache)
        {
            if (equals(icon, imageId))
            {
                mImageCache.remove(icon);
                mImageCache.addFirst(icon);
                
                return icon;
            }
        }
        
        ImageItem imageItem = mImageMap.get(imageId);
        
        if (imageItem == null)
            return null;
        
        ImageIcon icon = imageItem.getImage();
        mImageCache.addFirst(icon);
        
        if (mImageCache.size() > MAX_CACHED_IMAGES) // HACK check for memory usage instead
            mImageCache.removeLast();
        
        return icon;
    }
    
    public String addImage(InputStream in, String filename) throws IOException
    {
        ImageItem item = new ImageItem(in, filename);
        String id = item.getId();
        mImageMap.put(id, item);
        
        return id;
    }
    
    public String addImage(ImageIcon icon) throws IOException
    {
        String description = icon.getDescription();
        
        String id = "";
        if (description.startsWith(IMG_ID_PREFIX))
        {
            id = description.substring(IMG_ID_PREFIX.length());
        }
        else
        {
            InputStream in;
            String name = "";
            
            try
            {
                URL url = new URL(description);
                name = new File(url.getPath()).getName();
                in = url.openStream();
            }
            catch (MalformedURLException ex)
            {
                name = new File(description).getName();
                in = new FileInputStream(description);
                
                // fallthrough expected
            }
            
            id = addImage(in, name);
            icon.setDescription(IMG_ID_PREFIX + id);
        }
        
        return id;
    }
    
    /**
     * Converts the given list of image icons into a list of image IDs. This is
     * done by using the description field of ImageIcon. If the image icon was
     * already loaded from the image repository, the description will begin with
     * IMG_ID_PREFIX, otherwise it will be a new image that wasn't added to the
     * repository yet.
     */
    public List<String> addImages(List<ImageIcon> images)
    {
        List<String> imageIDs = new LinkedList<>();
        for (ImageIcon icon : images)
        {
            try
            {
                imageIDs.add(addImage(icon));
            }
            catch (IOException e)
            {
                Main.logThrowable("could not convert image to image-id", e);
            }
        }
        
        return imageIDs;
    }
    
    /**
     * Retains all images with given IDs. All other images are removed.
     */
    public void retain(Set<String> retainIDs)
    {
        Set<String> toBeRemoved = new HashSet<>(mImageMap.keySet());
        
        for (String id : retainIDs)
            toBeRemoved.remove(id);
        
        for (String id : toBeRemoved)
            mImageMap.remove(id);
    }
    
    public static boolean equals(ImageIcon image, String id)
    {
        String description = image.getDescription();
        return (description.startsWith(IMG_ID_PREFIX) && 
            description.substring(IMG_ID_PREFIX.length()).equals(id));
    }
    
    public static boolean equals(List<ImageIcon> images, List<String> ids)
    {
        if (images.size() != ids.size())
            return false;
        
        for (ImageIcon icon : images)
        {
            String id = "";
            String description = icon.getDescription();
            
            if (description.startsWith(IMG_ID_PREFIX))
            {
                id = description.substring(IMG_ID_PREFIX.length());
                
                if (!ids.contains(id))
                    return false;
            }
            else
            {
                return false;
            }
        }
        
        return true;
    }
    
    public List<ImageIcon> toImageIcons(List<String> ids)
    {
        List<ImageIcon> images = new LinkedList<ImageIcon>();
        
        if (ids == null)
            return images;
        
        for (String id : ids)
        {
            ImageIcon image = getImage(id);
            if (image != null)
                images.add(image);
        }
        
        return images;
    }
    
    public void clear()
    {
        mImageMap.clear();
    }
    
    private ImageRepository() // singleton
    {        
    }
}
