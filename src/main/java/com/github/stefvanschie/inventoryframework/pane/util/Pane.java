package com.github.stefvanschie.inventoryframework.pane.util;

import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.GuiLocation;
import com.github.stefvanschie.inventoryframework.util.XMLUtil;
import com.google.common.primitives.Primitives;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.apache.commons.codec.binary.Base64;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Cancellable;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The base class for all panes.
 */
public abstract class Pane {

    /**
     * The starting position of this pane
     */
    @NotNull
    protected GuiLocation start;

    /**
     * Length is horizontal, height is vertical
     */
    protected int length, height;

    /**
     * The visibility state of the pane
     */
    private boolean visible;

    /**
     * The priority of the pane, determines when it will be rendered
     */
    @NotNull
    private Priority priority;

    /**
     * The consumer that will be called once a players clicks in the gui
     */
    @Nullable
    protected Consumer<InventoryClickEvent> onLocalClick;

    /**
     * A map containing the mappings for properties for items
     */
    private static final Map<String, Function<String, Object>> PROPERTY_MAPPINGS = new HashMap<>();

    /**
     * Constructs a new default pane
     *
     * @param start the upper left corner of the pane
     * @param length the length of the pane
     * @param height the height of the pane
     * @param priority the priority of the pane
     */
    protected Pane(@NotNull GuiLocation start, int length, int height, @NotNull Priority priority) {
        assert start.getX() + length <= 9 : "length longer than maximum size";
        assert start.getY() + height <= 6 : "height longer than maximum size";

        this.start = start;

        this.length = length;
        this.height = height;

        this.priority = priority;
        this.visible = true;
    }

    /**
     * Constructs a new default pane
     *
     * @param start the upper left corner of the pane
     * @param length the length of the pane
     * @param height the height of the pane
     */
    protected Pane(@NotNull GuiLocation start, int length, int height) {
        this(start, length, height, Priority.NORMAL);
    }

    /**
     * Set the length of this pane
     *
     * @param length the new length
     */
    public void setLength(int length) {
        this.length = length;
    }

    /**
     * Set the height of this pane
     *
     * @param height the new height
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Set the x coordinate of this pane
     *
     * @param x the new x coordinate
     */
    public void setX(int x) {
        this.start = new GuiLocation(x, start.getY());
    }

    /**
     * Set the y coordinate of this pane
     *
     * @param y the new y coordinate
     */
    public void setY(int y) {
        this.start = new GuiLocation(start.getX(), y);
    }

    /**
     * Returns the length of this pane
     *
     * @return the length
     */
    @Contract(pure = true)
    public int getLength() {
        return length;
    }

    /**
     * Returns the height of this pane
     *
     * @return the height
     */
    @Contract(pure = true)
    public int getHeight() {
        return height;
    }

    /**
     * Gets the x coordinate of this pane
     *
     * @return the x coordinate
     */
    @Contract(pure = true)
    public int getX() {
        return start.getX();
    }

    /**
     * Gets the y coordinate of this pane
     *
     * @return the y coordinate
     */
    @Contract(pure = true)
    public int getY() {
        return start.getY();
    }

    /**
     * Has to set all the items in the right spot inside the inventory
     *
     * @param inventory the inventory that the items should be displayed in
     * @param paneOffsetX the pane's offset on the x axis
     * @param paneOffsetY the pane's offset on the y axis
     * @param maxLength the maximum length of the pane
     * @param maxHeight the maximum height of the pane
     */
    public abstract void display(Inventory inventory, int paneOffsetX, int paneOffsetY, int maxLength, int maxHeight);

    /**
     * Returns the pane's visibility state
     *
     * @return the pane's visibility
     */
    @Contract(pure = true)
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this pane is visible or not
     *
     * @param visible the pane's visibility
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Called whenever there is being clicked on this pane
     *
     * @param event the event that occurred while clicking on this item
     * @param paneOffsetX the pane's offset on the x axis
     * @param paneOffsetY the pane's offset on the y axis
     * @param maxLength the maximum length of the pane
     * @param maxHeight the maximum height of the pane
     * @return whether the item was found or not
     */
    public abstract boolean click(@NotNull InventoryClickEvent event, int paneOffsetX, int paneOffsetY, int maxLength,
                                  int maxHeight);

    /**
     * Sets the priority of this pane
     *
     * @param priority the priority
     */
    public void setPriority(@NotNull Priority priority) {
        this.priority = priority;
    }

    /**
     * Loads an item from an instance and an element
     *
     * @param instance the instance
     * @param element the element
     * @return the gui item
     */
    public static GuiItem loadItem(Object instance, Element element) {
        String id = element.getAttribute("id");
        ItemStack itemStack = new ItemStack(Material.matchMaterial(id.toUpperCase(Locale.getDefault())),
                element.hasAttribute("amount") ? Integer.parseInt(element.getAttribute("amount")) : 1,
                element.hasAttribute("damage") ? Short.parseShort(element.getAttribute("damage")) : 0);

        List<Object> properties = new ArrayList<>();

        if (element.hasChildNodes()) {
            NodeList childNodes = element.getChildNodes();

            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);

                if (item.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                Element elementItem = (Element) item;

                String nodeName = item.getNodeName();

                if (nodeName.equals("properties") || nodeName.equals("lore") || nodeName.equals("enchantments")) {
                    Element innerElement = (Element) item;
                    NodeList innerChildNodes = innerElement.getChildNodes();

                    for (int j = 0; j < innerChildNodes.getLength(); j++) {
                        Node innerNode = innerChildNodes.item(j);

                        if (innerNode.getNodeType() != Node.ELEMENT_NODE)
                            continue;

                        Element innerElementChild = (Element) innerNode;
                        ItemMeta itemMeta = itemStack.getItemMeta();

                        switch (nodeName) {
                            case "properties":
                                if (!innerNode.getNodeName().equals("property"))
                                    continue;

                                String propertyType;

                                if (!innerElementChild.hasAttribute("type"))
                                    propertyType = "string";
                                else
                                    propertyType = innerElementChild.getAttribute("type");

                                properties.add(PROPERTY_MAPPINGS.get(propertyType).apply(innerElementChild
                                        .getTextContent()));
                                break;
                            case "lore":
                                if (!innerNode.getNodeName().equals("line"))
                                    continue;

                                List<String> lore = itemMeta.hasLore() ? itemMeta.getLore() : new ArrayList<>();

                                lore.add(ChatColor.translateAlternateColorCodes('&', innerNode
                                        .getTextContent()));
                                itemMeta.setLore(lore);
                                itemStack.setItemMeta(itemMeta);
                                break;
                            case "enchantments":
                                if (!innerNode.getNodeName().equals("enchantment"))
                                    continue;

                                itemMeta.addEnchant(Enchantment.getByName(
                                        innerElementChild.getAttribute("id").toUpperCase(Locale.getDefault())
                                ), Integer.parseInt(innerElementChild.getAttribute("level")), true);
                                itemStack.setItemMeta(itemMeta);
                                break;
                        }
                    }
                } else if (nodeName.equals("displayname")) {
                    ItemMeta itemMeta = itemStack.getItemMeta();

                    itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', item
                            .getTextContent()));

                    itemStack.setItemMeta(itemMeta);
                } else if (nodeName.equals("skull") && itemStack.getItemMeta() instanceof SkullMeta) {
                    SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();

                    if (elementItem.hasAttribute("owner"))
                        skullMeta.setOwner(elementItem.getAttribute("owner"));
                    else if (elementItem.hasAttribute("id")) {
                        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
                        byte[] encodedData = Base64.encodeBase64(String.format("{textures:{SKIN:{url:\"%s\"}}}",
                                "http://textures.minecraft.net/texture/" + elementItem.getAttribute("id"))
                                .getBytes());
                        profile.getProperties().put("textures", new Property("textures", new String(encodedData)));

                        try {
                            Field profileField = skullMeta.getClass().getDeclaredField("profile");
                            profileField.setAccessible(true);
                            profileField.set(skullMeta, profile);
                        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    itemStack.setItemMeta(skullMeta);
                }
            }
        }

        Consumer<InventoryClickEvent> action = null;

        if (element.hasAttribute("onLocalClick")) {
            for (Method method : instance.getClass().getMethods()) {
                if (!method.getName().equals(element.getAttribute("onLocalClick")))
                    continue;

                int parameterCount = method.getParameterCount();
                Class<?>[] parameterTypes = method.getParameterTypes();

                if (parameterCount == 0)
                    action = event -> {
                        try {
                            //because reflection with lambdas is stupid
                            method.setAccessible(true);
                            method.invoke(instance);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    };
                else if (InventoryClickEvent.class.isAssignableFrom(parameterTypes[0]) ||
                    Cancellable.class.isAssignableFrom(parameterTypes[0])) {
                    if (parameterCount == 1)
                        action = event -> {
                            try {
                                //because reflection with lambdas is stupid
                                method.setAccessible(true);
                                method.invoke(instance, event);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        };
                    else if (parameterCount == properties.size() + 1) {
                        boolean correct = true;

                        for (int i = 0; i < properties.size(); i++) {
                            Object attribute = properties.get(i);

                            if (!(parameterTypes[1 + i].isPrimitive() &&
                                    Primitives.unwrap(attribute.getClass()).isAssignableFrom(parameterTypes[1 + i])) &&
                                    !attribute.getClass().isAssignableFrom(parameterTypes[1 + i]))
                                correct = false;
                        }

                        if (correct) {
                            action = event -> {
                                try {
                                    //don't ask me why we need to do this, just roll with it (actually I do know why, but it's stupid)
                                    properties.add(0, event);

                                    //because reflection with lambdas is stupid
                                    method.setAccessible(true);
                                    method.invoke(instance, properties.toArray(new Object[0]));

                                    //since we'll append the event to the list next time again, we need to remove it here again
                                    properties.remove(0);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    e.printStackTrace();
                                }
                            };
                        }
                    }
                }

                break;
            }
        }

        GuiItem item = action == null ? new GuiItem(itemStack) : new GuiItem(itemStack, action);

        if (element.hasAttribute("field")) {
            try {
                Field field = instance.getClass().getField(element.getAttribute("field"));

                field.setAccessible(true);
                field.set(instance, item);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        if (element.hasAttribute("populate")) {
            try {
                Method method = instance.getClass().getMethod("populate", GuiItem.class);

                method.setAccessible(true);
                method.invoke(instance, item);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        return item;
    }

    public static void load(Pane pane, Object instance, Element element) {
        if (element.hasAttribute("priority"))
            pane.setPriority(Priority.valueOf(element.getAttribute("priority")));

        if (element.hasAttribute("visible"))
            pane.setVisible(Boolean.parseBoolean(element.getAttribute("visible")));

        if (element.hasAttribute("field")) {
            try {
                Field field = instance.getClass().getField(element.getAttribute("field"));

                field.setAccessible(true);
                field.set(instance, pane);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        if (element.hasAttribute("onLocalClick"))
            pane.setOnLocalClick(XMLUtil.loadOnClickAttribute(instance, element));

        if (element.hasAttribute("populate")) {
            for (Method method: instance.getClass().getMethods()) {
                if (!method.getName().equals(element.getAttribute("populate")))
                    continue;

                try {
                    method.setAccessible(true);
                    method.invoke(instance, pane);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Returns the priority of the pane
     *
     * @return the priority
     */
    @NotNull
    public Priority getPriority() {
        return priority;
    }

    /**
     * Gets all the items in this pane and all underlying panes
     *
     * @return all items
     */
    @NotNull
    @Contract(pure = true)
    public abstract Collection<GuiItem> getItems();

    /**
     * Gets all the panes in this panes, including any child panes from other panes
     *
     * @return all panes
     */
    @NotNull
    @Contract(pure = true)
    public abstract Collection<Pane> getPanes();

    /**
     * Set the consumer that should be called whenever this gui is clicked in.
     *
     * @param onLocalClick the consumer that gets called
     */
    public void setOnLocalClick(@Nullable Consumer<InventoryClickEvent> onLocalClick) {
        this.onLocalClick = onLocalClick;
    }

    /**
     * Returns the property mappings used when loading properties from an XML file.
     *
     * @return the property mappings
     */
    @NotNull
    @Contract(pure = true)
    public static Map<String, Function<String, Object>> getPropertyMappings() {
        return PROPERTY_MAPPINGS;
    }

    /**
     * An enum representing the rendering priorities for the panes. Uses a similar system to Bukkit's
     * {@link org.bukkit.event.EventPriority} system
     */
    public enum Priority {

        /**
         * The lowest priority, will be rendered first
         */
        LOWEST,

        /**
         * A low priority, lower than default
         */
        LOW,

        /**
         * A normal priority, the default
         */
        NORMAL,

        /**
         * A higher priority, higher than default
         */
        HIGH,

        /**
         * The highest priority for production use
         */
        HIGHEST,

        /**
         * The highest priority, will always be called last, should not be used for production code
         */
        MONITOR
    }

    static {
        PROPERTY_MAPPINGS.put("boolean", Boolean::parseBoolean);
        PROPERTY_MAPPINGS.put("byte", Byte::parseByte);
        PROPERTY_MAPPINGS.put("character", value -> value.charAt(0));
        PROPERTY_MAPPINGS.put("double", Double::parseDouble);
        PROPERTY_MAPPINGS.put("float", Float::parseFloat);
        PROPERTY_MAPPINGS.put("integer", Integer::parseInt);
        PROPERTY_MAPPINGS.put("long", Long::parseLong);
        PROPERTY_MAPPINGS.put("short", Short::parseShort);
        PROPERTY_MAPPINGS.put("string", value -> value);
    }
}