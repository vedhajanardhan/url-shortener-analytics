package com.vedha.urlshortener.repository;

import com.vedha.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    @Query("SELECT FUNCTION('DATE', c.clickedAt) as day, COUNT(c) as cnt " +
           "FROM ClickEvent c WHERE c.shortCode = :shortCode AND c.clickedAt >= :since " +
           "GROUP BY FUNCTION('DATE', c.clickedAt) ORDER BY day")
    List<Object[]> countByDaySince(@Param("shortCode") String shortCode, @Param("since") LocalDateTime since);

    @Query("SELECT c.deviceType, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.deviceType")
    List<Object[]> countByDevice(@Param("shortCode") String shortCode);

    @Query("SELECT c.browser, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.browser")
    List<Object[]> countByBrowser(@Param("shortCode") String shortCode);

    @Query("SELECT c.country, COUNT(c) FROM ClickEvent c WHERE c.shortCode = :shortCode GROUP BY c.country")
    List<Object[]> countByCountry(@Param("shortCode") String shortCode);

    @Query("SELECT c.referrer, COUNT(c) as cnt FROM ClickEvent c WHERE c.shortCode = :shortCode " +
           "AND c.referrer IS NOT NULL GROUP BY c.referrer ORDER BY cnt DESC")
    List<Object[]> topReferrers(@Param("shortCode") String shortCode);

    @Query("SELECT DISTINCT c.shortCode FROM ClickEvent c WHERE c.clickedAt >= :since")
    List<String> findDistinctShortCodesWithActivitySince(@Param("since") LocalDateTime since);
}
