package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItunesAppMetaRepository extends JpaRepository<ItunesAppMeta, UUID>, JpaSpecificationExecutor<ItunesAppMeta> {
    Optional<ItunesAppMeta> findByTrackId(Long trackId);

    List<ItunesAppMeta> findAllByTrackIdIn(List<Long> trackIds);

    Optional<ItunesAppMeta> findByBundleId(String bundleIdResolved);

    List<ItunesAppMeta> findAllByBundleIdIn(List<String> bundleIds);

    @Query("SELECT DISTINCT i.primaryGenreName FROM ItunesAppMeta i WHERE i.primaryGenreName IS NOT NULL ORDER BY i.primaryGenreName")
    List<String> findDistinctGenres();

    @Query("SELECT i.primaryGenreName, COUNT(i) FROM ItunesAppMeta i WHERE i.primaryGenreName IS NOT NULL GROUP BY i.primaryGenreName ORDER BY COUNT(i) DESC")
    List<Object[]> findGenreCounts();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM app_supported_platforms WHERE app_id IN (SELECT id FROM itunes_app_meta WHERE track_id NOT IN (:trackIds))", nativeQuery = true)
    void deleteSupportedPlatformsNotIn(@Param("trackIds") List<Long> trackIds);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM itunes_app_meta WHERE track_id NOT IN (:trackIds)", nativeQuery = true)
    void deleteByTrackIdNotIn(@Param("trackIds") List<Long> trackIds);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM app_supported_platforms", nativeQuery = true)
    void deleteAllSupportedPlatforms();
}