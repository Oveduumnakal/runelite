package net.runelite.client.plugins.itemtracker;

import lombok.Data;

@Data
public class TrackedItem
{
    private final int itemId;
    private final String name;
    private int quantity;
    private long gePrice; // price per item in gp

    public long getTotalValue()
    {
        return (long) quantity * gePrice;
    }
}
