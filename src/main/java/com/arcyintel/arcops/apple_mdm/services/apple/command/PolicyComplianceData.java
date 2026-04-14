package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Redis-serializable data model for tracking policy compliance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyComplianceData implements Serializable {

    /**
     * Map of command UUID -> CommandTrackingData
     */
    private Map<String, CommandTrackingData> commands = new HashMap<>();

    /**
     * Set of completed command UUIDs
     */
    private Set<String> completed = new HashSet<>();

    /**
     * Map of failed command UUID -> CommandTrackingData
     */
    private Map<String, CommandTrackingData> failures = new HashMap<>();

    public void addCommand(String commandUuid, String commandType, String settingName) {
        commands.put(commandUuid, new CommandTrackingData(commandUuid, commandType, settingName, null));
    }

    public boolean markSuccess(String commandUuid) {
        if (!commands.containsKey(commandUuid)) {
            return false;
        }
        completed.add(commandUuid);
        return true;
    }

    public boolean markFailure(String commandUuid, String failureReason) {
        CommandTrackingData cmd = commands.get(commandUuid);
        if (cmd == null) {
            return false;
        }
        cmd.setFailureReason(failureReason);
        failures.put(commandUuid, cmd);
        completed.add(commandUuid);
        return true;
    }

    @JsonIgnore
    public boolean isComplete() {
        return completed.size() == commands.size();
    }

    /**
     * Individual command tracking data
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandTrackingData implements Serializable {
        private String commandUuid;
        private String commandType;
        private String settingName;
        private String failureReason;
    }
}
