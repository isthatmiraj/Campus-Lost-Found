package com.campus.lostfound.model;

public class Item {
    private final String id, reporterId, title, description, category, type, location, date, status, contactPhone, photoName;

    public Item(String id, String reporterId, String title, String description, String category,
                String type, String location, String date, String status, String contactPhone, String photoName) {
        this.id = id; this.reporterId = reporterId; this.title = title; this.description = description;
        this.category = category; this.type = type; this.location = location; this.date = date;
        this.status = status; this.contactPhone = contactPhone == null ? "" : contactPhone;
        this.photoName = photoName == null ? "" : photoName;
    }
    public Item(String id, String reporterId, String title, String description, String category,
                String type, String location, String date, String status, String contactPhone) {
        this(id, reporterId, title, description, category, type, location, date, status, contactPhone, "");
    }
    public Item(String id, String reporterId, String title, String description, String category,
                String type, String location, String date, String status) {
        this(id, reporterId, title, description, category, type, location, date, status, "", "");
    }
    public String getId() { return id; }
    public String getReporterId() { return reporterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public String getDate() { return date; }
    public String getStatus() { return status; }
    public String getContactPhone() { return contactPhone; }
    public String getPhotoName() { return photoName; }
}
