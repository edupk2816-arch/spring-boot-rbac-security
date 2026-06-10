package com.rbac.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "page_name")
    private String pageName;

    @Column(name = "url")
    private String url;

    // Comma-separated API URLs this page grants access to
    // e.g. "/api/users/**,/api/reports/**"
    @Column(name = "api_urls", columnDefinition = "TEXT")
    private String apiUrls;

    @Transient
    public List<String> getApiUrlsList() {
        if (apiUrls == null || apiUrls.isBlank()) return List.of();
        return Arrays.stream(apiUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
