package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.api.itunesapp.GetItunesAppDto;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.services.app.ItunesAppService;
import com.arcyintel.arcops.apple_mdm.services.mappers.ItunesAppMapper;
import com.arcyintel.arcops.apple_mdm.specifications.ItunesAppFilterSpecification;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.commons.utils.FieldFilterUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItunesAppServiceImpl implements ItunesAppService {

    private static final Logger logger = LoggerFactory.getLogger(ItunesAppServiceImpl.class);

    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final ItunesAppMapper itunesAppMapper;

    @Override
    public GetItunesAppDto getApp(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "App ID is required");
        }

        ItunesAppMeta app = itunesAppMetaRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("App not found: " + id));

        logger.info("Retrieved iTunes app with ID: {}", id);
        return itunesAppMapper.toDto(app);
    }

    @Override
    public GetItunesAppDto getAppByTrackId(Long trackId) {
        if (trackId == null) {
            throw new BusinessException("VALIDATION_ERROR", "Track ID is required");
        }

        ItunesAppMeta app = itunesAppMetaRepository.findByTrackId(trackId)
                .orElseThrow(() -> new EntityNotFoundException("App not found with trackId: " + trackId));

        logger.info("Retrieved iTunes app with trackId: {}", trackId);
        return itunesAppMapper.toDto(app);
    }

    @Override
    public GetItunesAppDto getAppByBundleId(String bundleId) {
        if (bundleId == null || bundleId.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Bundle ID is required");
        }

        ItunesAppMeta app = itunesAppMetaRepository.findByBundleId(bundleId)
                .orElseThrow(() -> new EntityNotFoundException("App not found with bundleId: " + bundleId));

        logger.info("Retrieved iTunes app with bundleId: {}", bundleId);
        return itunesAppMapper.toDto(app);
    }

    @Override
    public PagedModel<Map<String, Object>> listApps(DynamicListRequestDto request) {
        if (request.getPage() < 0 || request.getSize() <= 0) {
            logger.warn("Invalid pagination parameters: page={}, size={}.", request.getPage(), request.getSize());
            throw new BusinessException("VALIDATION_ERROR", "Invalid pagination parameters");
        }

        ItunesAppFilterSpecification spec = new ItunesAppFilterSpecification(
                request.getFilters(),
                request.isFuzzy(),
                request.getFuzzyThreshold()
        );
        Sort sort = Sort.unsorted();
        if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
            sort = request.isSortDesc()
                    ? Sort.by(Sort.Direction.DESC, request.getSortBy())
                    : Sort.by(Sort.Direction.ASC, request.getSortBy());
        }
        Page<ItunesAppMeta> apps = itunesAppMetaRepository.findAll(spec, PageRequest.of(request.getPage(), request.getSize(), sort));

        if (apps.isEmpty()) {
            logger.info("No iTunes apps found");
            return new PagedModel<>(Page.empty());
        }

        Set<String> fields = request.getFields();
        Page<Map<String, Object>> result = apps.map(app -> {
            GetItunesAppDto dto = itunesAppMapper.toDto(app);
            return FieldFilterUtil.filterFields(dto, fields);
        });

        logger.info("Successfully retrieved {} iTunes apps with {} fields.", result.getTotalElements(),
                fields == null || fields.isEmpty() ? "all" : fields.size());
        return new PagedModel<>(result);
    }

    @Override
    public List<String> getDistinctGenres() {
        return itunesAppMetaRepository.findDistinctGenres();
    }

    @Override
    public Map<String, Long> getGenreCounts() {
        List<Object[]> results = itunesAppMetaRepository.findGenreCounts();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : results) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

}
