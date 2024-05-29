package com.task10.model;

public class Tables {
    private int id;
    private int number;
    private int places;
    private boolean isVip;
    private Integer minOrder;

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNumber() {
        return this.number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getPlaces() {
        return this.places;
    }

    public void setPlaces(int places) {
        this.places = places;
    }

    public boolean isVip() {
        return this.isVip;
    }

    public void setIsVip(boolean isVip) {
        this.isVip = isVip;
    }

    public Integer getMinOrder() {
        return this.minOrder;
    }

    public void setMinOrder(Integer minOrder) {
        this.minOrder = minOrder;
    }
}
