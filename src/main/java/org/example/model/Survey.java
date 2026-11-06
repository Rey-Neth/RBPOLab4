package org.example.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Entity
@Table(name = "surveys")
public class Survey {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private User author;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 255)
    private String title;
    @Column(name = "author_id")
    private Long authorId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    @Column(length = 32)
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Survey() {}

    public Survey(Long id, String title, Long authorId, LocalDateTime startDate, LocalDateTime endDate,
                  String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.authorId = authorId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public Long getAuthorId() { return authorId; }

    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public LocalDateTime getStartDate() { return startDate; }

    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }

    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
