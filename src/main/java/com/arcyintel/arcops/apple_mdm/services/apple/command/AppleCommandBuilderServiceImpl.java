package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.arcyintel.arcops.apple_mdm.models.enums.PayloadIdentifiers;
import com.arcyintel.arcops.apple_mdm.models.profile.AppLock;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandBuilderService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Primary
public class AppleCommandBuilderServiceImpl implements AppleCommandBuilderService {

    @Override
    public NSDictionary deviceLock(String message, String phoneNumber, String commandUUID) {

        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Message", message);
                put("PhoneNumber", phoneNumber);
                put("RequestType", "DeviceLock");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary queryDeviceInformation(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Queries", new NSArray(
                        NSObject.fromJavaObject("UDID"),
                        NSObject.fromJavaObject("Languages"),
                        NSObject.fromJavaObject("Locales"),
                        NSObject.fromJavaObject("DeviceID"),
                        NSObject.fromJavaObject("OrganizationInfo"),
                        NSObject.fromJavaObject("LastCloudBackupDate"),
                        NSObject.fromJavaObject("AwaitingConfiguration"),
                        NSObject.fromJavaObject("MDMOptions"),
                        NSObject.fromJavaObject("iTunesStoreAccountIsActive"),
                        NSObject.fromJavaObject("iTunesStoreAccountHash"),
                        NSObject.fromJavaObject("DeviceName"),
                        NSObject.fromJavaObject("OSVersion"),
                        NSObject.fromJavaObject("BuildVersion"),
                        NSObject.fromJavaObject("ModelName"),
                        NSObject.fromJavaObject("Model"),
                        NSObject.fromJavaObject("ProductName"),
                        NSObject.fromJavaObject("SerialNumber"),
                        NSObject.fromJavaObject("DeviceCapacity"),
                        NSObject.fromJavaObject("AvailableDeviceCapacity"),
                        NSObject.fromJavaObject("BatteryLevel"),
                        NSObject.fromJavaObject("CellularTechnology"),
                        NSObject.fromJavaObject("ICCID"),
                        NSObject.fromJavaObject("BluetoothMAC"),
                        NSObject.fromJavaObject("WiFiMAC"),
                        NSObject.fromJavaObject("EthernetMACs"),
                        NSObject.fromJavaObject("CurrentCarrierNetwork"),
                        NSObject.fromJavaObject("SubscriberCarrierNetwork"),
                        NSObject.fromJavaObject("CurrentMCC"),
                        NSObject.fromJavaObject("CurrentMNC"),
                        NSObject.fromJavaObject("SubscriberMCC"),
                        NSObject.fromJavaObject("SubscriberMNC"),
                        NSObject.fromJavaObject("SIMMCC"),
                        NSObject.fromJavaObject("SIMMNC"),
                        NSObject.fromJavaObject("SIMCarrierNetwork"),
                        NSObject.fromJavaObject("CarrierSettingsVersion"),
                        NSObject.fromJavaObject("PhoneNumber"),
                        NSObject.fromJavaObject("DataRoamingEnabled"),
                        NSObject.fromJavaObject("VoiceRoamingEnabled"),
                        NSObject.fromJavaObject("PersonalHotspotEnabled"),
                        NSObject.fromJavaObject("IsRoaming"),
                        NSObject.fromJavaObject("IMEI"),
                        NSObject.fromJavaObject("MEID"),
                        NSObject.fromJavaObject("ModemFirmwareVersion"),
                        NSObject.fromJavaObject("IsSupervised"),
                        NSObject.fromJavaObject("IsDeviceLocatorServiceEnabled"),
                        NSObject.fromJavaObject("IsActivationLockEnabled"),
                        NSObject.fromJavaObject("IsDoNotDisturbInEffect"),
                        NSObject.fromJavaObject("EASDeviceIdentifier"),
                        NSObject.fromJavaObject("IsCloudBackupEnabled"),
                        NSObject.fromJavaObject("OSUpdateSettings"),
                        NSObject.fromJavaObject("LocalHostName"),
                        NSObject.fromJavaObject("HostName"),
                        NSObject.fromJavaObject("CatalogURL"),
                        NSObject.fromJavaObject("IsDefaultCatalog"),
                        NSObject.fromJavaObject("PreviousScanDate"),
                        NSObject.fromJavaObject("PreviousScanResult"),
                        NSObject.fromJavaObject("PerformPeriodicCheck"),
                        NSObject.fromJavaObject("AutomaticCheckEnabled"),
                        NSObject.fromJavaObject("BackgroundDownloadEnabled"),
                        NSObject.fromJavaObject("AutomaticAppInstallationEnabled"),
                        NSObject.fromJavaObject("AutomaticOSInstallationEnabled"),
                        NSObject.fromJavaObject("AutomaticSecurityUpdatesEnabled"),
                        NSObject.fromJavaObject("OSUpdateSettings"),
                        NSObject.fromJavaObject("LocalHostName"),
                        NSObject.fromJavaObject("HostName"),
                        NSObject.fromJavaObject("IsMultiUser"),
                        NSObject.fromJavaObject("IsMDMLostModeEnabled"),
                        NSObject.fromJavaObject("MaximumResidentUsers"),
                        NSObject.fromJavaObject("PushToken"),
                        NSObject.fromJavaObject("DiagnosticSubmissionEnabled"),
                        NSObject.fromJavaObject("AppAnalyticsEnabled"),
                        NSObject.fromJavaObject("IsNetworkTethered"),
                        NSObject.fromJavaObject("ServiceSubscriptions")
                ));
                put("RequestType", "DeviceInformation");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary querySecurityInformation(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "SecurityInfo");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary installApp(String identifier, boolean removable, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Attributes", new NSDictionary() {{
                    put("AssociatedDomainsEnableDirectDownloads", false);
                    put("Removable", removable);
                }});
                put("Identifier", identifier);
                put("InstallAsManaged", true);
                put("ManagementFlags", 5);
                put("Options", new NSDictionary() {{
                    put("PurchaseMethod", 0);
                }});
                put("RequestRequiresNetworkTether", false);
                put("RequestType", "InstallApplication");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary installApp(Integer identifier, boolean removable, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Attributes", new NSDictionary() {{
                    put("AssociatedDomainsEnableDirectDownloads", false);
                    put("Removable", removable);
                }});
                put("iTunesStoreID", identifier);
                put("InstallAsManaged", true);
                put("ManagementFlags", 5);
                put("Options", new NSDictionary() {{
                    put("PurchaseMethod", 1);
                }});
                put("RequestRequiresNetworkTether", false);
                put("RequestType", "InstallApplication");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary installAppFromManifest(String manifestUrl, boolean removable, String commandUUID) {
        return installAppFromManifest(manifestUrl, removable, commandUUID, null);
    }

    @Override
    public NSDictionary installAppFromManifest(String manifestUrl, boolean removable, String commandUUID, NSDictionary configuration) {
        // Enterprise app installation via ManifestURL
        // https://developer.apple.com/documentation/devicemanagement/installapplicationcommand
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Attributes", new NSDictionary() {{
                    put("Removable", removable);
                }});
                put("ManifestURL", manifestUrl);
                put("InstallAsManaged", true);
                put("ManagementFlags", 5);
                put("RequestRequiresNetworkTether", false);
                put("RequestType", "InstallApplication");
                if (configuration != null) {
                    put("Configuration", configuration);
                }
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary installEnterpriseApp(String manifestUrl, String commandUUID) {
        // macOS-only: InstallEnterpriseApplication command
        // https://developer.apple.com/documentation/devicemanagement/install-enterprise-application-command
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("ManifestURL", manifestUrl);
                put("InstallAsManaged", true);
                put("RequestType", "InstallEnterpriseApplication");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary installProfile(String payload, String commandUUID) throws IOException {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Payload", new NSData(payload));
                put("RequestType", "InstallProfile");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    // RemoveApplication builder (Identifier = bundleId)
    public NSDictionary removeProfile(String identifier, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Identifier", identifier);
                put("RequestType", "RemoveProfile");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    // RemoveApplication builder (Identifier = bundleId)
    public NSDictionary removeApplication(String identifier, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("Identifier", identifier);
                put("RequestType", "RemoveApplication");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary listInstalledProfiles(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "ProfileList");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary singleAppMode(AppLock.SingleAppConfig singleAppConfig) {
        return new NSDictionary() {{
            put("PayloadContent", new NSArray(new NSDictionary() {{
                put("App", new NSDictionary() {{
                    put("Identifier", singleAppConfig.getIdentifier());
                    put("Options", new NSDictionary() {{
                        put("DisableAutoLock", singleAppConfig.getOptions().isDisableAutoLock());
                        put("DisableDeviceRotation", singleAppConfig.getOptions().isDisableDeviceRotation());
                        put("DisableRingerSwitch", singleAppConfig.getOptions().isDisableRingerSwitch());
                        put("DisableSleepWakeButton", singleAppConfig.getOptions().isDisableSleepWakeButton());
                        put("DisableTouch", singleAppConfig.getOptions().isDisableTouch());
                        put("DisableVolumeButtons", singleAppConfig.getOptions().isDisableVolumeButtons());
                        put("EnableAssistiveTouch", singleAppConfig.getOptions().isEnableAssistiveTouch());
                        put("EnableInvertColors", singleAppConfig.getOptions().isEnableInvertColors());
                        put("EnableMonoAudio", singleAppConfig.getOptions().isEnableMonoAudio());
                        put("EnableSpeakSelection", singleAppConfig.getOptions().isEnableSpeakSelection());
                        put("EnableVoiceOver", singleAppConfig.getOptions().isEnableVoiceOver());
                        put("EnableZoom", singleAppConfig.getOptions().isEnableZoom());
                        put("EnableVoiceControl", singleAppConfig.getOptions().isEnableVoiceControl());
                    }});
                    put("UserEnabledOptions", new NSDictionary() {{
                        put("AssistiveTouch", singleAppConfig.getUserEnabledOptions().isAssistiveTouch());
                        put("InvertColors", singleAppConfig.getUserEnabledOptions().isInvertColors());
                        put("VoiceOver", singleAppConfig.getUserEnabledOptions().isVoiceOver());
                        put("Zoom", singleAppConfig.getUserEnabledOptions().isZoom());
                        put("VoiceControl", singleAppConfig.getUserEnabledOptions().isVoiceControl());
                    }});
                }});
                put("PayloadIdentifier", PayloadIdentifiers.APPLOCK.getPayloadIdentifier());
                put("PayloadType", "com.apple.app.lock");
                put("PayloadUUID", UUID.randomUUID().toString());
                put("PayloadVersion", 1);
            }}));
            put("PayloadDisplayName", "Single App Mode");
            put("PayloadIdentifier", PayloadIdentifiers.SINGLE_APP_MODE.getPayloadIdentifier());
            put("PayloadType", "Configuration");
            put("PayloadUUID", UUID.randomUUID().toString());
            put("PayloadVersion", 1);
        }};
    }

    @Override
    public NSDictionary declarativeManagement(String syncTokensBase64, String commandUUID) throws IOException {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "DeclarativeManagement");
                put("Data", new NSData(syncTokensBase64));
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary settings(List<NSDictionary> settings, String commandUUID) {
        NSDictionary root = new NSDictionary();
        root.put("CommandUUID", commandUUID);

        NSDictionary command = new NSDictionary();
        command.put("RequestType", "Settings");

        NSArray settingsArray = new NSArray(settings.size());
        for (int i = 0; i < settings.size(); i++) {
            settingsArray.setValue(i, settings.get(i));
        }
        command.put("Settings", settingsArray);

        root.put("Command", command);
        return root;
    }

    @Override
    public NSDictionary restartDevice(String commandUUID, Boolean notifyUser) {
        NSDictionary command = new NSDictionary();
        command.put("RequestType", "RestartDevice");

        // NotifyUser is optional; if true, the device displays a confirmation dialog to the user.
        // Applicable mainly for iOS/tvOS. For macOS, KioskModeAppDisable usage varies,
        // but standard restart usually doesn't require extra flags.
        if (notifyUser != null) {
            command.put("NotifyUser", notifyUser);
        }

        NSDictionary root = new NSDictionary();
        root.put("Command", command);
        root.put("CommandUUID", commandUUID);

        return root;
    }

    @Override
    public NSDictionary shutDownDevice(String commandUUID) {
        NSDictionary command = new NSDictionary();
        command.put("RequestType", "ShutDownDevice");

        NSDictionary root = new NSDictionary();
        root.put("Command", command);
        root.put("CommandUUID", commandUUID);

        return root;
    }

    @Override
    public NSDictionary eraseDevice(String commandUUID, String pin, boolean preserveDataPlan) {
        NSDictionary command = new NSDictionary();
        command.put("RequestType", "EraseDevice");

        // To preserve eSIM data plans (iOS 11+)
        command.put("PreserveDataPlan", preserveDataPlan);

        // PIN code for macOS devices (Required to unlock the device if it locks after erasure via EFI lock)
        if (pin != null && !pin.isBlank()) {
            command.put("PIN", pin);
        }

        // Optional: DisallowProximitySetup prevents the 'Move from Android' or 'Quick Start' options after wipe (iOS 11+)
        // command.put("DisallowProximitySetup", true);

        NSDictionary root = new NSDictionary();
        root.put("Command", command);
        root.put("CommandUUID", commandUUID);

        return root;
    }

    @Override
    public NSDictionary installedApplicationList(String commandUUID) {
        NSDictionary command = new NSDictionary();
        command.put("RequestType", "InstalledApplicationList");

        // Explicitly fetching all apps, not just managed ones.
        // Note: Items/Identifiers array can be added here to filter specific apps if needed.
        command.put("ManagedAppsOnly", false);

        NSDictionary root = new NSDictionary();
        root.put("Command", command);
        root.put("CommandUUID", commandUUID);

        return root;
    }

    @Override
    public NSDictionary managedApplicationList(String commandUUID) {
        NSDictionary command = new NSDictionary();
        command.put("RequestType", "ManagedApplicationList");

        // Note: This command is historically used to retrieve managed apps.
        // Alternatively, 'InstalledApplicationList' with 'ManagedAppsOnly = true' is also commonly used.

        NSDictionary root = new NSDictionary();
        root.put("Command", command);
        root.put("CommandUUID", commandUUID);

        return root;
    }

    @Override
    public NSDictionary deviceConfigured(String commandUUID) {
        NSDictionary command = new NSDictionary();
        command.put("RequestType", "DeviceConfigured");

        NSDictionary root = new NSDictionary();
        root.put("Command", command);
        root.put("CommandUUID", commandUUID);

        return root;
    }

    @Override
    public NSDictionary clearPasscode(String unlockToken, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "ClearPasscode");
                // The UnlockToken is stored as Base64 in DB, so we decode it to bytes for the plist
                if (unlockToken != null) {
                    put("UnlockToken", new NSData(Base64.getDecoder().decode(unlockToken)));
                }
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary clearRestrictionsPassword(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "ClearRestrictionsPassword");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary enableLostMode(String message, String phoneNumber, String footnote, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "EnableLostMode");

                // Message is technically required by logic, though protocol allows empty
                if (message != null && !message.isBlank()) {
                    put("Message", message);
                }

                if (phoneNumber != null && !phoneNumber.isBlank()) {
                    put("PhoneNumber", phoneNumber);
                }

                if (footnote != null && !footnote.isBlank()) {
                    put("Footnote", footnote);
                }
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary disableLostMode(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "DisableLostMode");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary deviceLocation(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "DeviceLocation");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary playLostModeSound(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "PlayLostModeSound");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary certificateList(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "CertificateList");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    public NSDictionary userList(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "UserList");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary logOutUser(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "LogOutUser");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary deleteUser(String userName, boolean forceDeletion, String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "DeleteUser");
                put("UserName", userName);
                put("ForceDeletion", forceDeletion);
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary enableRemoteDesktop(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "EnableRemoteDesktop");
            }});
            put("CommandUUID", commandUUID);
        }};
    }

    @Override
    public NSDictionary disableRemoteDesktop(String commandUUID) {
        return new NSDictionary() {{
            put("Command", new NSDictionary() {{
                put("RequestType", "DisableRemoteDesktop");
            }});
            put("CommandUUID", commandUUID);
        }};
    }
}
