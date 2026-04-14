package com.arcyintel.arcops.apple_mdm.configs.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SwaggerExampleCustomizer implements OperationCustomizer {

    private static final String EXAMPLES_DIR = "swagger-examples/";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Map<String, String>> OPERATION_EXAMPLES = new LinkedHashMap<>();

    static {
        // AppleAccountController
        OPERATION_EXAMPLES.put("createAccount", orderedMap(
                "Basic Account", "create-account-basic.json",
                "Full Account", "create-account-complex.json"
        ));
        OPERATION_EXAMPLES.put("updateAccount", orderedMap(
                "Basic Update", "update-account-basic.json",
                "Full Update", "update-account-complex.json"
        ));
        OPERATION_EXAMPLES.put("listAccounts", orderedMap(
                "Basic List", "list-accounts-basic.json",
                "Filtered List", "list-accounts-complex.json",
                "Fuzzy Search", "list-accounts-fuzzy.json"
        ));

        // AppGroupController
        OPERATION_EXAMPLES.put("create", orderedMap(
                "Basic App Group", "create-app-group-basic.json",
                "Full App Group with Items", "create-app-group-complex.json"
        ));
        OPERATION_EXAMPLES.put("update", orderedMap(
                "Basic Update", "update-app-group-basic.json",
                "Full Update with Items", "update-app-group-complex.json"
        ));
        OPERATION_EXAMPLES.put("addItem", orderedMap(
                "VPP App", "add-app-group-item-basic.json",
                "Enterprise App", "add-app-group-item-complex.json"
        ));
        OPERATION_EXAMPLES.put("replaceItems", orderedMap(
                "Single Item", "replace-app-group-items-basic.json",
                "Multiple Items", "replace-app-group-items-complex.json"
        ));
        OPERATION_EXAMPLES.put("listAppGroups", orderedMap(
                "Basic List", "list-app-groups-basic.json",
                "Filtered List", "list-app-groups-complex.json",
                "Fuzzy Search", "list-app-groups-fuzzy.json"
        ));

        // ItunesAppController
        OPERATION_EXAMPLES.put("listApps", orderedMap(
                "Basic List", "list-itunes-apps-basic.json",
                "Filter by Platform", "list-itunes-apps-complex.json",
                "Fuzzy Search (typo tolerant)", "list-itunes-apps-fuzzy.json"
        ));

        // AbmController
        OPERATION_EXAMPLES.put("disownDevices", orderedMap(
                "Single Device", "disown-devices-basic.json",
                "Multiple Devices", "disown-devices-complex.json"
        ));
        OPERATION_EXAMPLES.put("createProfile", orderedMap(
                "Basic Profile", "create-abm-profile-basic.json",
                "Full Profile", "create-abm-profile-complex.json"
        ));
        OPERATION_EXAMPLES.put("removeProfileFromDevices", orderedMap(
                "Basic Remove", "remove-profile-from-devices-basic.json",
                "Remove with Reassign", "remove-profile-from-devices-complex.json"
        ));
        OPERATION_EXAMPLES.put("assignAssetsToDevices", orderedMap(
                "Single Assignment", "assign-assets-basic.json",
                "Bulk Assignment", "assign-assets-complex.json"
        ));
        OPERATION_EXAMPLES.put("disassociateAssetsFromDevices", orderedMap(
                "Single Disassociation", "assign-assets-basic.json",
                "Bulk Disassociation", "assign-assets-complex.json"
        ));
    }

    private static Map<String, String> orderedMap(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        String methodName = handlerMethod.getMethod().getName();
        Map<String, String> examples = OPERATION_EXAMPLES.get(methodName);
        if (examples == null) {
            return operation;
        }

        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null) {
            return operation;
        }

        Content content = requestBody.getContent();
        if (content == null) {
            return operation;
        }

        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.get("*/*");
        }
        if (mediaType == null) {
            return operation;
        }

        Map<String, io.swagger.v3.oas.models.examples.Example> exampleMap = mediaType.getExamples();
        if (exampleMap == null) {
            exampleMap = new LinkedHashMap<>();
            mediaType.setExamples(exampleMap);
        }

        for (Map.Entry<String, String> entry : examples.entrySet()) {
            Object parsedExample = loadExample(entry.getValue());
            if (parsedExample != null) {
                io.swagger.v3.oas.models.examples.Example example = new io.swagger.v3.oas.models.examples.Example();
                example.setValue(parsedExample);
                exampleMap.put(entry.getKey(), example);
            }
        }

        return operation;
    }

    private Object loadExample(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(EXAMPLES_DIR + fileName);
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, Object.class);
            }
        } catch (IOException e) {
            return null;
        }
    }
}