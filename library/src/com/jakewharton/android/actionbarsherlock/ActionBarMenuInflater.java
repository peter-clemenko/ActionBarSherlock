/*
 * Copyright (C) 2006 The Android Open Source Project
 *               2011 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jakewharton.android.actionbarsherlock;

import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.app.Activity;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.InflateException;

/**
 * This class is used to instantiate menu XML files into Menu objects.
 * <p>
 * For performance reasons, menu inflation relies heavily on pre-processing of
 * XML files that is done at build time. Therefore, it is not currently possible
 * to use MenuInflater with an XmlPullParser over a plain XML file at runtime;
 * it only works with an XmlPullParser returned from a compiled resource (R.
 * <em>something</em> file.)
 */
public class ActionBarMenuInflater {
	/** Android XML namespace. */
	private static final String XML_NS = "http://schemas.android.com/apk/res/android";
	
    /** Menu tag name in XML. */
    private static final String XML_MENU = "menu";
    
    /** Group tag name in XML. */
    private static final String XML_GROUP = "group";
    
    /** Item tag name in XML. */
    private static final String XML_ITEM = "item";

    private static final int NO_ID = 0;
    
    private Context mContext;
    
    /**
     * Constructs a menu inflater.
     * 
     * @see Activity#getMenuInflater()
     */
    public ActionBarMenuInflater(Context context) {
        mContext = context;
    }

    /**
     * Inflate a menu hierarchy from the specified XML resource. Throws
     * {@link InflateException} if there is an error.
     * 
     * @param menuRes Resource ID for an XML layout resource to load (e.g.,
     *            <code>R.menu.main_activity</code>)
     * @param menu The Menu to inflate into. The items and submenus will be
     *            added to this Menu.
     */
    public void inflate(int menuRes, ActionBarMenu menu) {
        XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getLayout(menuRes);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            
            parseMenu(parser, attrs, menu);
        } catch (XmlPullParserException e) {
            throw new InflateException("Error inflating menu XML", e);
        } catch (IOException e) {
            throw new InflateException("Error inflating menu XML", e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    /**
     * Called internally to fill the given menu. If a sub menu is seen, it will
     * call this recursively.
     */
    private void parseMenu(XmlPullParser parser, AttributeSet attrs, ActionBarMenu menu)
            throws XmlPullParserException, IOException {
        ActionBarMenuState menuState = new ActionBarMenuState(menu);

        int eventType = parser.getEventType();
        String tagName;
        boolean lookingForEndOfUnknownTag = false;
        String unknownTagName = null;

        // This loop will skip to the menu start tag
        do {
            if (eventType == XmlPullParser.START_TAG) {
                tagName = parser.getName();
                if (tagName.equals(XML_MENU)) {
                    // Go to next tag
                    eventType = parser.next();
                    break;
                }
                
                throw new RuntimeException("Expecting menu, got " + tagName);
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);
        
        boolean reachedEndOfMenu = false;
        while (!reachedEndOfMenu) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (lookingForEndOfUnknownTag) {
                        break;
                    }
                    
                    tagName = parser.getName();
                    if (tagName.equals(XML_GROUP)) {
                        menuState.readGroup(attrs);
                    } else if (tagName.equals(XML_ITEM)) {
                        menuState.readItem(attrs);
                    } else if (tagName.equals(XML_MENU)) {
                        // A menu start tag denotes a submenu for an item
                        ActionBarSubMenu subMenu = menuState.addSubMenuItem();

                        // Parse the submenu into returned SubMenu
                        parseMenu(parser, attrs, subMenu);
                    } else {
                        lookingForEndOfUnknownTag = true;
                        unknownTagName = tagName;
                    }
                    break;
                    
                case XmlPullParser.END_TAG:
                    tagName = parser.getName();
                    if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
                        lookingForEndOfUnknownTag = false;
                        unknownTagName = null;
                    } else if (tagName.equals(XML_GROUP)) {
                        menuState.resetGroup();
                    } else if (tagName.equals(XML_ITEM)) {
                        // Add the item if it hasn't been added (if the item was
                        // a submenu, it would have been added already)
                        if (!menuState.hasAddedItem()) {
                            menuState.addItem();
                        }
                    } else if (tagName.equals(XML_MENU)) {
                        reachedEndOfMenu = true;
                    }
                    break;
                    
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document");
            }
            
            eventType = parser.next();
        }
    }
    
    /**
     * State for the current menu.
     * <p>
     * Groups can not be nested unless there is another menu (which will have
     * its state class).
     */
    private class ActionBarMenuState {
        private ActionBarMenu menu;

        /*
         * Group state is set on items as they are added, allowing an item to
         * override its group state. (As opposed to set on items at the group end tag.)
         */
        private int groupId;
        private int groupCategory;
        private int groupOrder;
        private int groupCheckable;
        private boolean groupVisible;
        private boolean groupEnabled;

        private boolean itemAdded;
        private int itemId;
        private int itemCategoryOrder;
        private String itemTitle;
        private String itemTitleCondensed;
        private int itemIconResId;
        private char itemAlphabeticShortcut;
        private char itemNumericShortcut;
        /**
         * Sync to attrs.xml enum:
         * - 0: none
         * - 1: all
         * - 2: exclusive
         */
        private int itemCheckable;
        private boolean itemChecked;
        private boolean itemVisible;
        private boolean itemEnabled;
        //private String itemOnClick;
        private int itemShowAsAction;
        //private int itemActionLayout;
        //private String itemActionViewClassName;
        
        private static final int defaultGroupId = NO_ID;
        private static final int defaultItemId = NO_ID;
        private static final int defaultItemCategory = 0;
        private static final int defaultItemOrder = 0;
        private static final int defaultItemCheckable = 0;
        private static final boolean defaultItemChecked = false;
        private static final boolean defaultItemVisible = true;
        private static final boolean defaultItemEnabled = true;
        private static final int defaultItemShowAsAction = 0;
        
        /** Mirror of package-scoped Menu.CATEGORY_MASK. */
        private static final int Menu__CATEGORY_MASK = 0xffff0000;
        /** Mirror of package-scoped Menu.USER_MASK. */
        private static final int Menu__USER_MASK = 0x0000ffff;
        
        public ActionBarMenuState(final ActionBarMenu menu) {
            this.menu = menu;
            
            resetGroup();
        }
        
        public void resetGroup() {
            groupId = defaultGroupId;
            groupCategory = defaultItemCategory;
            groupOrder = defaultItemOrder;
            groupCheckable = defaultItemCheckable;
            groupVisible = defaultItemVisible;
            groupEnabled = defaultItemEnabled;
        }

        /**
         * Called when the parser is pointing to a group tag.
         */
        public void readGroup(AttributeSet attrs) {
            //TypedArray a = mContext.obtainStyledAttributes(attrs, com.android.internal.R.styleable.MenuGroup);
            
            //groupId = a.getResourceId(com.android.internal.R.styleable.MenuGroup_id, defaultGroupId);
        	groupId = attrs.getAttributeResourceValue(XML_NS, "id", defaultGroupId);
            
            //groupCategory = a.getInt(com.android.internal.R.styleable.MenuGroup_menuCategory, defaultItemCategory);
        	groupCategory = attrs.getAttributeIntValue(XML_NS, "menuCategory", defaultItemCategory);
            
            //groupOrder = a.getInt(com.android.internal.R.styleable.MenuGroup_orderInCategory, defaultItemOrder);
        	groupOrder = attrs.getAttributeIntValue(XML_NS, "orderInCategory", defaultItemOrder);
            
            //groupCheckable = a.getInt(com.android.internal.R.styleable.MenuGroup_checkableBehavior, defaultItemCheckable);
        	groupCheckable = attrs.getAttributeIntValue(XML_NS, "checkableBehavior", defaultItemCheckable);
            
            //groupVisible = a.getBoolean(com.android.internal.R.styleable.MenuGroup_visible, defaultItemVisible);
        	groupVisible = attrs.getAttributeBooleanValue(XML_NS, "visible", defaultItemVisible);
            
            //groupEnabled = a.getBoolean(com.android.internal.R.styleable.MenuGroup_enabled, defaultItemEnabled);
        	groupEnabled = attrs.getAttributeBooleanValue(XML_NS, "enabled", defaultItemEnabled);

            //a.recycle();
        }
        
        /**
         * Called when the parser is pointing to an item tag.
         */
        public void readItem(AttributeSet attrs) {
            //TypedArray a = mContext.obtainStyledAttributes(attrs, com.android.internal.R.styleable.MenuItem);

            // Inherit attributes from the group as default value
            
            //itemId = a.getResourceId(com.android.internal.R.styleable.MenuItem_id, defaultItemId);
            itemId = attrs.getAttributeResourceValue(XML_NS, "id", defaultItemId);
            
            //final int category = a.getInt(com.android.internal.R.styleable.MenuItem_menuCategory, groupCategory);
            final int category = attrs.getAttributeIntValue(XML_NS, "menuCategory", groupCategory);
            
            //final int order = a.getInt(com.android.internal.R.styleable.MenuItem_orderInCategory, groupOrder);
            final int order = attrs.getAttributeIntValue(XML_NS, "orderInCategory", groupOrder);
            
            //itemCategoryOrder = (category & Menu.CATEGORY_MASK) | (order & Menu.USER_MASK);
            itemCategoryOrder = (category & Menu__CATEGORY_MASK) | (order & Menu__USER_MASK);
            
            //itemTitle = a.getString(com.android.internal.R.styleable.MenuItem_title);
            itemTitle = attrs.getAttributeValue(XML_NS, "title");
            
            //itemTitleCondensed = a.getString(com.android.internal.R.styleable.MenuItem_titleCondensed);
            itemTitleCondensed = attrs.getAttributeValue(XML_NS, "titleCondensed");
            
            //itemIconResId = a.getResourceId(com.android.internal.R.styleable.MenuItem_icon, 0);
            itemIconResId = attrs.getAttributeResourceValue(XML_NS, "icon", 0);
            
            //itemAlphabeticShortcut = getShortcut(a.getString(com.android.internal.R.styleable.MenuItem_alphabeticShortcut));
            itemAlphabeticShortcut = getShortcut(attrs.getAttributeValue(XML_NS, "alphabeticShortcut"));
            
            //itemNumericShortcut = getShortcut(a.getString(com.android.internal.R.styleable.MenuItem_numericShortcut));
            itemNumericShortcut = getShortcut(attrs.getAttributeValue(XML_NS, "numericShortcut"));
            
            //if (a.hasValue(com.android.internal.R.styleable.MenuItem_checkable)) {
            if (attrs.getAttributeValue(XML_NS, "checkable") != null) {
                // Item has attribute checkable, use it
                //itemCheckable = a.getBoolean(com.android.internal.R.styleable.MenuItem_checkable, false) ? 1 : 0;
            	itemCheckable = attrs.getAttributeBooleanValue(XML_NS, "checkable", false) ? 1 : 0;
            } else {
                // Item does not have attribute, use the group's (group can have one more state
                // for checkable that represents the exclusive checkable)
                itemCheckable = groupCheckable;
            }
            
            //itemChecked = a.getBoolean(com.android.internal.R.styleable.MenuItem_checked, defaultItemChecked);
            itemChecked = attrs.getAttributeBooleanValue(XML_NS, "checked", defaultItemChecked);
            
            //itemVisible = a.getBoolean(com.android.internal.R.styleable.MenuItem_visible, groupVisible);
            itemVisible = attrs.getAttributeBooleanValue(XML_NS, "visible", groupVisible);
            
            //itemEnabled = a.getBoolean(com.android.internal.R.styleable.MenuItem_enabled, groupEnabled);
            itemEnabled = attrs.getAttributeBooleanValue(XML_NS, "enabled", groupEnabled);
            
            //presumed emulation of 3.0+'s MenuInflator:
            //TODO: itemOnClick = attrs.getAttributeValue(XML_NS, "onClick");
            itemShowAsAction = attrs.getAttributeIntValue(XML_NS, "showAsAction", defaultItemShowAsAction);
            //TODO: itemActionLayout = attrs.getAttributeResourceValue(XML_NS, "actionLayout", 0);
            //TODO: itemActionViewClassName = attrs.getAttributeValue(XML_NS, "actionViewClass");
            
            //a.recycle();
            
            itemAdded = false;
        }

        private char getShortcut(String shortcutString) {
            if (shortcutString == null) {
                return 0;
            } else {
                return shortcutString.charAt(0);
            }
        }
        
        private void setItem(ActionBarMenuItem item) {
            item.setChecked(itemChecked)
                .setVisible(itemVisible)
                .setEnabled(itemEnabled)
                .setCheckable(itemCheckable >= 1)
                .setTitleCondensed(itemTitleCondensed)
                .setIcon(itemIconResId)
                .setAlphabeticShortcut(itemAlphabeticShortcut)
                .setNumericShortcut(itemNumericShortcut);
            
            //Not sure why this method has a return type of void
            item.setShowAsAction(itemShowAsAction);

            /*
            if (itemCheckable >= 2) {
                item.setExclusiveCheckable(true);
            }
            */
        }
        
        public void addItem() {
            itemAdded = true;
            setItem(menu.add(groupId, itemId, itemCategoryOrder, itemTitle));
        }
        
        public ActionBarSubMenu addSubMenuItem() {
            itemAdded = true;
            ActionBarSubMenu subMenu = menu.addSubMenu(groupId, itemId, itemCategoryOrder, itemTitle);
            setItem(subMenu.getItem());
            return subMenu;
        }
        
        public boolean hasAddedItem() {
            return itemAdded;
        }
    }
}