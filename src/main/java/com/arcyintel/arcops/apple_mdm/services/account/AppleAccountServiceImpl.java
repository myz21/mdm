package com.arcyintel.arcops.apple_mdm.services.account;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceInformation;
import com.arcyintel.arcops.apple_mdm.event.publisher.AccountEventPublisher;
import com.arcyintel.arcops.apple_mdm.models.api.account.CreateAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.models.api.account.GetAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.models.api.account.UpdateAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.repositories.AppleAccountRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.services.account.AppleAccountService;
import com.arcyintel.arcops.apple_mdm.services.mappers.AppleAccountMapper;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import com.arcyintel.arcops.commons.events.account.AccountCreatedEvent;
import com.arcyintel.arcops.commons.events.account.AccountDeletedEvent;
import com.arcyintel.arcops.commons.events.account.AccountSyncEvent;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.exceptions.ConflictException;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.commons.specifications.GenericFilterSpecification;
import com.arcyintel.arcops.commons.utils.FieldFilterUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.IOS;

@Service
@RequiredArgsConstructor
public class AppleAccountServiceImpl implements AppleAccountService {

    private static final Logger logger = LoggerFactory.getLogger(AppleAccountServiceImpl.class);

    private final AppleAccountRepository accountRepository;
    private final AppleDeviceRepository deviceRepository;
    private final AccountEventPublisher accountEventPublisher;
    private final AppleAccountMapper appleAccountMapper;

    @Override
    public void createAccount(CreateAppleAccountDto dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Username is required");
        }

        AppleAccount account = AppleAccount.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .managedAppleId(dto.getManagedAppleId())
                .fullName(dto.getFullName())
                .build();

        accountRepository.save(account);
        logger.info("AppleAccount created: {}", account.getUsername());

        publishSyncEvent(account);
    }

    @Override
    public void updateAccount(String id, UpdateAppleAccountDto dto) {
        AppleAccount account = accountRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        if (dto.getUsername() != null) account.setUsername(dto.getUsername());
        if (dto.getEmail() != null) account.setEmail(dto.getEmail());
        if (dto.getManagedAppleId() != null) account.setManagedAppleId(dto.getManagedAppleId());
        if (dto.getFullName() != null) account.setFullName(dto.getFullName());

        accountRepository.save(account);
        logger.info("AppleAccount updated: {}", id);

        publishSyncEvent(account);
    }

    @Override
    public void deleteAccount(String id) {
        AppleAccount account = accountRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        UUID accountId = account.getId();
        accountRepository.delete(account);
        logger.info("AppleAccount deleted: {}", id);

        accountEventPublisher.publishAccountDeletedEvent(
                AccountDeletedEvent.builder()
                        .accountId(accountId)
                        .platform(IOS)
                        .build()
        );
    }

    @Override
    public GetAppleAccountDto getAccount(String id) {
        AppleAccount account = accountRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        return appleAccountMapper.toDto(account);
    }

    @Override
    public List<GetAppleAccountDto> getAccounts() {
        return accountRepository.findByStatus("ACTIVE").stream()
                .map(appleAccountMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public PagedModel<Map<String, Object>> listAccounts(DynamicListRequestDto request) {
        if (request.getPage() < 0 || request.getSize() <= 0) {
            logger.warn("Invalid pagination parameters: page={}, size={}.", request.getPage(), request.getSize());
            throw new BusinessException("VALIDATION_ERROR", "Invalid pagination parameters");
        }

        GenericFilterSpecification<AppleAccount> spec = new GenericFilterSpecification<>(
                request.getFilters(), request.isFuzzy(), request.getFuzzyThreshold());
        Page<AppleAccount> accounts = accountRepository.findAll(spec, PageRequest.of(request.getPage(), request.getSize()));

        if (accounts.isEmpty()) {
            logger.info("No accounts found");
            return new PagedModel<>(Page.empty());
        }

        Set<String> fields = request.getFields();
        Page<Map<String, Object>> result = accounts.map(account -> {
            GetAppleAccountDto dto = appleAccountMapper.toDto(account);
            return FieldFilterUtil.filterFields(dto, fields);
        });

        logger.info("Successfully retrieved {} accounts with {} fields.", result.getTotalElements(),
                fields == null || fields.isEmpty() ? "all" : fields.size());
        return new PagedModel<>(result);
    }

    @Override
    public void assignAccountToDevice(String accountId, String deviceId) {
        AppleAccount account = accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        AppleDevice device = deviceRepository.findById(UUID.fromString(deviceId))
                .orElseThrow(() -> new EntityNotFoundException("Device not found"));

        // Check multi-user support
        boolean isMultiUser = false;
        AppleDeviceInformation info = device.getDeviceProperties();
        if (info != null && Boolean.TRUE.equals(info.getMultiUser())) {
            isMultiUser = true;
        }

        if (!isMultiUser && device.getAccounts() != null && !device.getAccounts().isEmpty()) {
            // Check if the device already has this account
            boolean alreadyAssigned = device.getAccounts().stream()
                    .anyMatch(a -> a.getId().equals(account.getId()));
            if (alreadyAssigned) {
                throw new ConflictException("Account is already assigned to this device");
            }
            throw new BusinessException("VALIDATION_ERROR",
                    "This device does not support multiple users. Remove the existing account first.");
        }

        account.getDevices().add(device);
        accountRepository.save(account);
        logger.info("Account {} assigned to device {}", accountId, deviceId);

        publishSyncEvent(account);

        // Publish AccountCreatedEvent to back_core if identity is linked
        if (account.getIdentity() != null) {
            accountEventPublisher.publishAccountCreatedEvent(
                    AccountCreatedEvent.builder()
                            .accountId(account.getId())
                            .identityId(account.getIdentity().getId())
                            .deviceId(device.getId())
                            .platform(IOS)
                            .build()
            );
            logger.info("AccountCreatedEvent published: identityId={}, deviceId={}", account.getIdentity().getId(), deviceId);
        }
    }

    @Override
    public void removeAccountFromDevice(String accountId, String deviceId) {
        AppleAccount account = accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        boolean removed = account.getDevices().removeIf(d -> d.getId().equals(UUID.fromString(deviceId)));
        if (!removed) {
            throw new EntityNotFoundException("Account is not assigned to this device");
        }

        accountRepository.save(account);
        logger.info("Account {} removed from device {}", accountId, deviceId);

        publishSyncEvent(account);
    }

    private void publishSyncEvent(AppleAccount account) {
        List<UUID> deviceIds = account.getDevices().stream()
                .map(AppleDevice::getId)
                .collect(Collectors.toList());

        accountEventPublisher.publishAccountSyncEvent(
                AccountSyncEvent.builder()
                        .accountId(account.getId())
                        .username(account.getUsername())
                        .email(account.getEmail())
                        .fullName(account.getFullName())
                        .status(account.getStatus())
                        .platform(IOS)
                        .deviceIds(deviceIds)
                        .build()
        );
    }

}