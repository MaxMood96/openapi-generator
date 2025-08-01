/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Sets;
import com.samskivert.mustache.Mustache.Lambda;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Assertions;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.GlobalSettings;
import org.openapitools.codegen.languages.SpringCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.templating.mustache.*;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.SemVer;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class DefaultCodegenTest {

    private static final String MEDIA_TYPE = "mediaType";
    private static final String APP_JSON = "application/json";
    private static final String APP_XML = "application/xml";
    private static final String APP_TEXT = "application/text";
    private static final Logger testLogger = (Logger) LoggerFactory.getLogger(ModelUtils.class);

    @Test
    public void testDeeplyNestedAdditionalPropertiesImports() {
        final DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openApi = TestUtils.parseFlattenSpec("src/test/resources/3_0/additional-properties-deeply-nested.yaml");
        codegen.setOpenAPI(openApi);
        PathItem path = openApi.getPaths().get("/ping");
        CodegenOperation operation = codegen.fromOperation("/ping", "post", path.getPost(), path.getServers());
        assertEquals(1, Sets.intersection(operation.imports, Sets.newHashSet("Person")).size());
    }

    @Test
    public void testOptionalResponseImports() {
        final DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openApi = TestUtils.parseFlattenSpec("src/test/resources/3_0/optionalResponse.yaml");
        codegen.setOpenAPI(openApi);
        PathItem path = openApi.getPaths().get("/api/Users/{userId}");
        CodegenOperation operation = codegen.fromOperation("/api/Users/{userId}", "get", path.getGet(), path.getServers());
        assertTrue(operation.isResponseOptional);
    }

    @Test
    public void testEnumImports() {
        final DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openApi = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_12445.yaml");
        codegen.setOpenAPI(openApi);
        PathItem path = openApi.getPaths().get("/pets/petType/{type}");
        CodegenOperation operation = codegen.fromOperation("/pets/petType/{type}", "get", path.getGet(), path.getServers());
        assertEquals(1, Sets.intersection(operation.imports, Sets.newHashSet("PetByType")).size());
    }

    @Test
    public void testHasBodyParameter() {
        final Schema refSchema = new Schema<>().$ref("#/components/schemas/Pet");
        Operation pingOperation = new Operation()
                .responses(
                        new ApiResponses().addApiResponse("204", new ApiResponse()
                                .description("Ok response")));
        Operation createOperation = new Operation()
                .requestBody(new RequestBody()
                        .content(new Content().addMediaType(APP_JSON,
                                new MediaType().schema(refSchema))))
                .responses(
                        new ApiResponses().addApiResponse("201", new ApiResponse()
                                .description("Created response")));
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        openAPI.getComponents().addSchemas("Pet", new ObjectSchema());

        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        assertFalse(codegen.hasBodyParameter(pingOperation));
        assertTrue(codegen.hasBodyParameter(createOperation));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testParameterEmptyDescription() {
        DefaultCodegen codegen = new DefaultCodegen();

        codegen.fromRequestBody(null, new HashSet<>(), null);
    }

    @Test
    public void testGetConsumesInfoAndGetProducesInfo() {
        final Schema refSchema = new Schema<>().$ref("#/components/schemas/Pet");
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        openAPI.getComponents().addSchemas("Pet", new ObjectSchema());
        openAPI.getComponents().addRequestBodies("MyRequestBody", new RequestBody()
                .content(new Content().addMediaType(APP_JSON,
                        new MediaType().schema(refSchema))));
        openAPI.getComponents().addResponses("MyResponse", new ApiResponse()
                .description("Ok response")
                .content(new Content().addMediaType(APP_XML,
                        new MediaType().schema(refSchema))));

        Operation createOperation = new Operation()
                .requestBody(new RequestBody()
                        .content(new Content()
                                .addMediaType(APP_JSON, new MediaType().schema(refSchema))
                                .addMediaType(APP_XML, new MediaType().schema(refSchema))
                        ))
                .responses(
                        new ApiResponses().addApiResponse("201", new ApiResponse()
                                .description("Created response")));
        Set<String> createConsumesInfo = DefaultCodegen.getConsumesInfo(openAPI, createOperation);
        assertEquals(2, createConsumesInfo.size());
        assertTrue(createConsumesInfo.contains(APP_JSON), "contains 'application/json'");
        assertTrue(createConsumesInfo.contains(APP_XML), "contains 'application/xml'");
        Set<String> createProducesInfo = DefaultCodegen.getProducesInfo(openAPI, createOperation);
        assertEquals(0, createProducesInfo.size());
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        CodegenOperation coCreate = codegen.fromOperation("somepath", "post", createOperation, null);
        assertTrue(coCreate.hasConsumes);
        assertEquals(2, coCreate.consumes.size());
        assertFalse(coCreate.hasProduces);

        Operation updateOperationWithRef = new Operation()
                .requestBody(new RequestBody().$ref("#/components/requestBodies/MyRequestBody"))
                .responses(new ApiResponses().addApiResponse("201", new ApiResponse().$ref("#/components/responses/MyResponse")));
        Set<String> updateConsumesInfo = DefaultCodegen.getConsumesInfo(openAPI, updateOperationWithRef);
        assertEquals(1, updateConsumesInfo.size());
        assertTrue(updateConsumesInfo.contains(APP_JSON), "contains 'application/json'");
        Set<String> updateProducesInfo = DefaultCodegen.getProducesInfo(openAPI, updateOperationWithRef);
        assertEquals(1, updateProducesInfo.size());
        assertTrue(updateProducesInfo.contains(APP_XML), "contains 'application/xml'");

        CodegenOperation coUpdate = codegen.fromOperation("somepath", "post", updateOperationWithRef, null);
        assertTrue(coUpdate.hasConsumes);
        assertEquals(1, coUpdate.consumes.size());
        assertEquals(APP_JSON, coUpdate.consumes.get(0).get(MEDIA_TYPE));
        assertTrue(coUpdate.hasProduces);
        assertEquals(1, coUpdate.produces.size(), 1);
        assertEquals(APP_XML, coUpdate.produces.get(0).get(MEDIA_TYPE));
    }

    @Test
    public void testGetProducesInfo() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/produces.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Operation textOperation = openAPI.getPaths().get("/ping/text").getGet();
        CodegenOperation coText = codegen.fromOperation("/ping/text", "get", textOperation, null);
        assertTrue(coText.hasProduces);
        assertEquals(1, coText.produces.size());
        assertEquals("text/plain", coText.produces.get(0).get(MEDIA_TYPE));

        Operation jsonOperation = openAPI.getPaths().get("/ping/json").getGet();
        CodegenOperation coJson = codegen.fromOperation("/ping/json", "get", jsonOperation, null);
        assertTrue(coJson.hasProduces);
        assertEquals(1, coJson.produces.size());
        assertEquals(APP_JSON, coJson.produces.get(0).get(MEDIA_TYPE));

        Operation issue443Operation = openAPI.getPaths().get("/other/issue443").getGet();
        CodegenOperation coIssue443 = codegen.fromOperation("/other/issue443", "get", issue443Operation, null);
        assertTrue(coIssue443.hasProduces);
        assertEquals(2, coIssue443.produces.size());
        assertEquals(APP_JSON, coIssue443.produces.get(0).get(MEDIA_TYPE));
        assertEquals(APP_TEXT, coIssue443.produces.get(1).get(MEDIA_TYPE));
    }

    @Test
    public void testInitialConfigValues() throws Exception {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.processOpts();

        assertEquals(Boolean.TRUE, codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP));
        assertTrue(codegen.isHideGenerationTimestamp());
    }

    @Test
    public void testSettersForConfigValues() throws Exception {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setHideGenerationTimestamp(false);
        codegen.processOpts();

        assertEquals(Boolean.FALSE, codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP));
        assertFalse(codegen.isHideGenerationTimestamp());
    }

    @Test
    public void testAdditionalPropertiesPutForConfigValues() throws Exception {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.additionalProperties().put(CodegenConstants.HIDE_GENERATION_TIMESTAMP, false);
        codegen.processOpts();

        assertEquals(Boolean.FALSE, codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP));
        assertFalse(codegen.isHideGenerationTimestamp());
    }

    @Test
    public void testArraySchemaIsNotIncludedInAliases() {
        final DefaultCodegen codegen = new DefaultCodegen();
        Map<String, Schema> schemas = new HashMap<String, Schema>() {
            {
                put("ArraySchemaTest", new ArraySchema());
            }

        };

        Map<String, String> aliases = codegen.getAllAliases(schemas);

        assertEquals(0, aliases.size());
    }

    @Test
    public void testFormParameterHasDefaultValue() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/petstore-with-fake-endpoints-models-for-testing.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Schema requestBodySchema = ModelUtils.getReferencedSchema(openAPI,
                ModelUtils.getSchemaFromRequestBody(openAPI.getPaths().get("/fake").getGet().getRequestBody()));
        CodegenParameter codegenParameter = codegen.fromFormProperty("enum_form_string", (Schema) requestBodySchema.getProperties().get("enum_form_string"), new HashSet<String>());

        assertEquals("-efg", codegenParameter.defaultValue);
        Assertions.assertNull(codegenParameter.getSchema());
    }

    @Test
    public void testDateTimeFormParameterHasDefaultValue() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/spring/date-time-parameter-types-for-testing.yml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Schema requestBodySchema = ModelUtils.getSchemaFromRequestBody(openAPI.getPaths().get("/thingy/{date}").getPost().getRequestBody());
        // dereference
        requestBodySchema = ModelUtils.getReferencedSchema(openAPI, requestBodySchema);
        CodegenParameter codegenParameter = codegen.fromFormProperty("visitDate", (Schema) requestBodySchema.getProperties().get("visitDate"),
                new HashSet<>());

        assertEquals("1971-12-19T03:39:57-08:00", codegenParameter.defaultValue);
        Assertions.assertNull(codegenParameter.getSchema());
    }

    @Test
    public void testOriginalOpenApiDocumentVersion() {
        // Test with OAS 2.0 document.
        String location = "src/test/resources/2_0/python-prior/petstore-with-fake-endpoints-models-for-testing.yaml";
        OpenAPI openAPI = TestUtils.parseFlattenSpec(location);
        SemVer version = ModelUtils.getOpenApiVersion(openAPI, location, null);
        assertEquals(new SemVer("2.0.0"), version);

        // Test with OAS 3.0 document.
        location = "src/test/resources/3_0/python/petstore-with-fake-endpoints-models-for-testing-with-http-signature.yaml";
        openAPI = TestUtils.parseFlattenSpec(location);
        version = ModelUtils.getOpenApiVersion(openAPI, location, null);
        assertEquals(new SemVer("3.0.0"), version);
    }

    @Test
    public void testAdditionalPropertiesV2SpecDisallowAdditionalPropertiesIfNotPresentTrue() {
        // this is the legacy config that most of our tooling uses
        OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/additional-properties-for-testing.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(true);

        Schema schema = openAPI.getComponents().getSchemas().get("AdditionalPropertiesClass");
        Assertions.assertNull(schema.getAdditionalProperties());

        Schema addProps = ModelUtils.getAdditionalProperties(schema);
        // The petstore-with-fake-endpoints-models-for-testing.yaml does not set the
        // 'additionalProperties' keyword for this model, hence assert the value to be null.
        Assertions.assertNull(addProps);
        CodegenModel cm = codegen.fromModel("AdditionalPropertiesClass", schema);
        Assertions.assertNull(cm.getAdditionalProperties());
        // When the 'additionalProperties' keyword is not present, the model
        // should allow undeclared properties. However, due to bug
        // https://github.com/swagger-api/swagger-parser/issues/1369, the swagger
        // converter does not retain the value of the additionalProperties.

        Map<String, Schema> modelPropSchemas = schema.getProperties();
        Schema map_string_sc = modelPropSchemas.get("map_string");
        CodegenProperty map_string_cp = null;
        Schema map_with_additional_properties_sc = modelPropSchemas.get("map_with_additional_properties");
        CodegenProperty map_with_additional_properties_cp = null;
        Schema map_without_additional_properties_sc = modelPropSchemas.get("map_without_additional_properties");
        CodegenProperty map_without_additional_properties_cp = null;

        for (CodegenProperty cp : cm.vars) {
            if ("map_string".equals(cp.baseName)) {
                map_string_cp = cp;
            } else if ("map_with_additional_properties".equals(cp.baseName)) {
                map_with_additional_properties_cp = cp;
            } else if ("map_without_additional_properties".equals(cp.baseName)) {
                map_without_additional_properties_cp = cp;
            }
        }

        // map_string
        // This property has the following inline schema.
        // additionalProperties:
        //   type: string
        Assertions.assertNotNull(map_string_sc);
        Assertions.assertNotNull(map_string_sc.getAdditionalProperties());
        Assertions.assertNotNull(map_string_cp.getAdditionalProperties());

        // map_with_additional_properties
        // This property has the following inline schema.
        // additionalProperties: true
        Assertions.assertNotNull(map_with_additional_properties_sc);
        // It is unfortunate that child.getAdditionalProperties() returns null for a V2 schema.
        // We cannot differentiate between 'additionalProperties' not present and
        // additionalProperties: true.
        // UPDATE fixed in swagger parser 2.1.14
        assertTrue((Boolean) map_with_additional_properties_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_with_additional_properties_sc);
        assertEquals(new Schema(), addProps);
        Assertions.assertNotNull(map_with_additional_properties_cp.getAdditionalProperties());

        // map_without_additional_properties
        // This property has the following inline schema.
        // additionalProperties: false
        Assertions.assertNotNull(map_without_additional_properties_sc);
        // It is unfortunate that child.getAdditionalProperties() returns null for a V2 schema.
        // We cannot differentiate between 'additionalProperties' not present and
        // additionalProperties: false.
        // UPDATE fixed in swagger parser 2.1.14
        assertFalse((Boolean) map_without_additional_properties_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_without_additional_properties_sc);
        Assertions.assertNull(addProps);
        Assertions.assertNull(map_without_additional_properties_cp.getAdditionalProperties());

        // check of composed schema model
        String schemaName = "Parent";
        schema = openAPI.getComponents().getSchemas().get(schemaName);
        cm = codegen.fromModel(schemaName, schema);
        Assertions.assertNull(cm.getAdditionalProperties());
    }

    @Test
    public void testAdditionalPropertiesV2SpecDisallowAdditionalPropertiesIfNotPresentFalse() {
        OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/additional-properties-for-testing.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);
        codegen.supportsAdditionalPropertiesWithComposedSchema = true;
        /*
        When this DisallowAdditionalPropertiesIfNotPresent is false:
        for CodegenModel/CodegenParameter/CodegenProperty/CodegenResponse.getAdditionalProperties
        if the input additionalProperties is False or unset (null)
        .getAdditionalProperties is set to AnyTypeSchema

        For the False value this is incorrect, but it is the best that we can do because of this bug:
        https://github.com/swagger-api/swagger-parser/issues/1369 where swagger parser
        sets both null/False additionalProperties to null
         */

        Schema schema = openAPI.getComponents().getSchemas().get("AdditionalPropertiesClass");
        Assertions.assertNull(schema.getAdditionalProperties());

        Schema addProps = ModelUtils.getAdditionalProperties(schema);
        // The petstore-with-fake-endpoints-models-for-testing.yaml does not set the
        // 'additionalProperties' keyword for this model, hence assert the value to be null.
        Assertions.assertNull(addProps);
        CodegenModel cm = codegen.fromModel("AdditionalPropertiesClass", schema);
        Assertions.assertNotNull(cm.getAdditionalProperties());
        // When the 'additionalProperties' keyword is not present, the model
        // should allow undeclared properties. However, due to bug
        // https://github.com/swagger-api/swagger-parser/issues/1369, the swagger
        // converter does not retain the value of the additionalProperties.

        Map<String, Schema> modelPropSchemas = schema.getProperties();
        Schema map_string_sc = modelPropSchemas.get("map_string");
        CodegenProperty map_string_cp = null;
        Schema map_with_additional_properties_sc = modelPropSchemas.get("map_with_additional_properties");
        CodegenProperty map_with_additional_properties_cp = null;
        Schema map_without_additional_properties_sc = modelPropSchemas.get("map_without_additional_properties");
        CodegenProperty map_without_additional_properties_cp = null;

        for (CodegenProperty cp : cm.vars) {
            if ("map_string".equals(cp.baseName)) {
                map_string_cp = cp;
            } else if ("map_with_additional_properties".equals(cp.baseName)) {
                map_with_additional_properties_cp = cp;
            } else if ("map_without_additional_properties".equals(cp.baseName)) {
                map_without_additional_properties_cp = cp;
            }
        }

        // map_string
        // This property has the following inline schema.
        // additionalProperties:
        //   type: string
        Assertions.assertNotNull(map_string_sc);
        Assertions.assertNotNull(map_string_sc.getAdditionalProperties());
        Assertions.assertNotNull(map_string_cp.getAdditionalProperties());

        // map_with_additional_properties
        // This property has the following inline schema.
        // additionalProperties: true
        Assertions.assertNotNull(map_with_additional_properties_sc);
        // It is unfortunate that child.getAdditionalProperties() returns null for a V2 schema.
        // We cannot differentiate between 'additionalProperties' not present and
        // additionalProperties: true.
        // UPDATE fixed in swagger parser 2.1.14
        assertTrue((Boolean) map_with_additional_properties_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_with_additional_properties_sc);
        assertEquals(new Schema(), addProps);
        Assertions.assertNotNull(map_with_additional_properties_cp.getAdditionalProperties());

        // map_without_additional_properties
        // This property has the following inline schema.
        // additionalProperties: false
        Assertions.assertNotNull(map_without_additional_properties_sc);
        // It is unfortunate that child.getAdditionalProperties() returns null for a V2 schema.
        // We cannot differentiate between 'additionalProperties' not present and
        // additionalProperties: false.
        // UPDATE fixed in swagger parser 2.1.14
        assertFalse((Boolean) map_without_additional_properties_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_without_additional_properties_sc);
        Assertions.assertNull(addProps);
        Assertions.assertNull(map_without_additional_properties_cp.getAdditionalProperties());

        // check of composed schema model
        String schemaName = "Parent";
        schema = openAPI.getComponents().getSchemas().get(schemaName);
        cm = codegen.fromModel(schemaName, schema);
        Assertions.assertNotNull(cm.getAdditionalProperties());
    }

    @Test
    public void testAdditionalPropertiesV3SpecDisallowAdditionalPropertiesIfNotPresentFalse() {
        OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/python/petstore-with-fake-endpoints-models-for-testing-with-http-signature.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);
        codegen.supportsAdditionalPropertiesWithComposedSchema = true;
        codegen.setOpenAPI(openAPI);

        Schema componentSchema = openAPI.getComponents().getSchemas().get("AdditionalPropertiesClass");
        Assertions.assertNull(componentSchema.getAdditionalProperties());

        // When the 'additionalProperties' keyword is not present, the schema may be
        // extended with any undeclared properties.
        Schema addProps = ModelUtils.getAdditionalProperties(componentSchema);
        Assertions.assertNotNull(addProps);
        assertEquals(new Schema(), addProps);
        CodegenModel cm = codegen.fromModel("AdditionalPropertiesClass", componentSchema);
        Assertions.assertNotNull(cm.getAdditionalProperties());

        Map<String, Schema> modelPropSchemas = componentSchema.getProperties();
        Schema map_with_undeclared_properties_string_sc = modelPropSchemas.get("map_with_undeclared_properties_string");
        CodegenProperty map_with_undeclared_properties_string_cp = null;
        Schema map_with_undeclared_properties_anytype_1_sc = modelPropSchemas.get("map_with_undeclared_properties_anytype_1");
        CodegenProperty map_with_undeclared_properties_anytype_1_cp = null;
        Schema map_with_undeclared_properties_anytype_2_sc = modelPropSchemas.get("map_with_undeclared_properties_anytype_2");
        CodegenProperty map_with_undeclared_properties_anytype_2_cp = null;
        Schema map_with_undeclared_properties_anytype_3_sc = modelPropSchemas.get("map_with_undeclared_properties_anytype_3");
        CodegenProperty map_with_undeclared_properties_anytype_3_cp = null;
        Schema empty_map_sc = modelPropSchemas.get("empty_map");
        CodegenProperty empty_map_cp = null;

        for (CodegenProperty cp : cm.vars) {
            if ("map_with_undeclared_properties_string".equals(cp.baseName)) {
                map_with_undeclared_properties_string_cp = cp;
            } else if ("map_with_undeclared_properties_anytype_1".equals(cp.baseName)) {
                map_with_undeclared_properties_anytype_1_cp = cp;
            } else if ("map_with_undeclared_properties_anytype_2".equals(cp.baseName)) {
                map_with_undeclared_properties_anytype_2_cp = cp;
            } else if ("map_with_undeclared_properties_anytype_3".equals(cp.baseName)) {
                map_with_undeclared_properties_anytype_3_cp = cp;
            } else if ("empty_map".equals(cp.baseName)) {
                empty_map_cp = cp;
            }
        }

        // map_with_undeclared_properties_string
        // This property has the following inline schema.
        // additionalProperties:
        //   type: string
        Assertions.assertNotNull(map_with_undeclared_properties_string_sc);
        Assertions.assertNotNull(map_with_undeclared_properties_string_sc.getAdditionalProperties());
        Assertions.assertNotNull(map_with_undeclared_properties_string_cp.getAdditionalProperties());

        // map_with_undeclared_properties_anytype_1
        // This property does not use the additionalProperties keyword,
        // which means by default undeclared properties are allowed.
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_1_sc);
        Assertions.assertNull(map_with_undeclared_properties_anytype_1_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_with_undeclared_properties_anytype_1_sc);
        Assertions.assertNotNull(addProps);
        assertEquals(new Schema(), addProps);
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_1_cp.getAdditionalProperties());

        // map_with_undeclared_properties_anytype_2
        // This property does not use the additionalProperties keyword,
        // which means by default undeclared properties are allowed.
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_2_sc);
        Assertions.assertNull(map_with_undeclared_properties_anytype_2_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_with_undeclared_properties_anytype_2_sc);
        Assertions.assertNotNull(addProps);
        assertEquals(new Schema(), addProps);
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_2_cp.getAdditionalProperties());

        // map_with_undeclared_properties_anytype_3
        // This property has the following inline schema.
        // additionalProperties: true
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_3_sc);
        // Unlike the V2 spec, in V3 we CAN differentiate between 'additionalProperties' not present and
        // additionalProperties: true.
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_3_sc.getAdditionalProperties());
        assertEquals(Boolean.TRUE, map_with_undeclared_properties_anytype_3_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(map_with_undeclared_properties_anytype_3_sc);
        Assertions.assertNotNull(addProps);
        assertEquals(new Schema(), addProps);
        Assertions.assertNotNull(map_with_undeclared_properties_anytype_3_cp.getAdditionalProperties());

        // empty_map
        // This property has the following inline schema.
        // additionalProperties: false
        Assertions.assertNotNull(empty_map_sc);
        // Unlike the V2 spec, in V3 we CAN differentiate between 'additionalProperties' not present and
        // additionalProperties: false.
        Assertions.assertNotNull(empty_map_sc.getAdditionalProperties());
        assertEquals(Boolean.FALSE, empty_map_sc.getAdditionalProperties());
        addProps = ModelUtils.getAdditionalProperties(empty_map_sc);
        Assertions.assertNull(addProps);
        Assertions.assertNull(empty_map_cp.getAdditionalProperties());

        // check of composed schema model
        String schemaName = "SomeObject";
        Schema schema = openAPI.getComponents().getSchemas().get(schemaName);
        cm = codegen.fromModel(schemaName, schema);
        Assertions.assertNotNull(cm.getAdditionalProperties());
    }

    @Test
    public void testAdditionalPropertiesV3SpecDisallowAdditionalPropertiesIfNotPresentTrue() {
        // As per OAS spec, when the 'additionalProperties' keyword is not present, the schema may be
        // extended with any undeclared properties.
        // However, in legacy 'additionalProperties' mode, this is interpreted as
        // 'no additional properties are allowed'.
        OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/petstore-with-fake-endpoints-models-for-testing-with-http-signature.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setDisallowAdditionalPropertiesIfNotPresent(true);
        codegen.setOpenAPI(openAPI);

        Schema schema = openAPI.getComponents().getSchemas().get("AdditionalPropertiesClass");
        Assertions.assertNull(schema.getAdditionalProperties());

        Schema addProps = ModelUtils.getAdditionalProperties(schema);
        Assertions.assertNull(addProps);
    }

    @Test
    public void testEnsureNoDuplicateProduces() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/two-responses.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Operation operation = openAPI.getPaths().get("/test").getGet();
        CodegenOperation co = codegen.fromOperation("/test", "get", operation, null);

        assertEquals(1, co.produces.size());
        assertEquals(APP_JSON, co.produces.get(0).get(MEDIA_TYPE));
    }

    @Test
    public void testConsistentParameterNameAfterUniquenessRename() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        Operation operation = new Operation()
                .operationId("opId")
                .addParametersItem(new QueryParameter().name("myparam").schema(new StringSchema()))
                .addParametersItem(new QueryParameter().name("myparam").schema(new StringSchema()))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));

        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        CodegenOperation co = codegen.fromOperation("/some/path", "get", operation, null);
        assertEquals("/some/path", co.path);
        assertEquals(2, co.allParams.size());
        List<String> allParamsNames = co.allParams.stream().map(p -> p.paramName).collect(Collectors.toList());
        assertTrue(allParamsNames.contains("myparam"));
        assertTrue(allParamsNames.contains("myparam2"));
        List<String> queryParamsNames = co.queryParams.stream().map(p -> p.paramName).collect(Collectors.toList());
        assertTrue(queryParamsNames.contains("myparam"));
        assertTrue(queryParamsNames.contains("myparam2"));
    }

    @Test
    public void testUniquenessRenameOfFormParameters() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/form-duplicated-parameter.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        Operation operation = openAPI.getPaths().get("/form-param-poc/{id}").getPut();
        CodegenOperation co = codegen.fromOperation("/form-param-poc/{id}", "put", operation, null);
        assertEquals("/form-param-poc/{id}", co.path);
        assertEquals(2, co.allParams.size());
        List<String> allParamsNames = co.allParams.stream().map(p -> p.paramName).collect(Collectors.toList());
        assertTrue(allParamsNames.contains("id"));
        assertTrue(allParamsNames.contains("id2"));
    }

    @Test
    public void testGetSchemaTypeWithComposedSchemaWithOneOf() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/composed-oneof.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();

        Operation operation = openAPI.getPaths().get("/state").getPost();
        Schema schema = ModelUtils.getReferencedSchema(openAPI,
                ModelUtils.getSchemaFromRequestBody(operation.getRequestBody()));
        String type = codegen.getSchemaType(schema);

        Assertions.assertNotNull(type);
        assertEquals("oneOf<ObjA,ObjB>", type);
    }

    @Test
    public void testOneOfEnum() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue12545.json");
        final DefaultCodegen codegen = new DefaultCodegen();
        String modelName = "petItems";

        final Schema schema = openAPI.getComponents().getSchemas().get(modelName);
        codegen.setOpenAPI(openAPI);
        CodegenModel petItems = codegen.fromModel(modelName, schema);

        Set<String> oneOf = new TreeSet<>();
        oneOf.add("Dog");
        oneOf.add("Cat");
        assertEquals(petItems.oneOf, oneOf);
        // make sure that animal has the property type
        boolean typeSeen = false;
        boolean typeContainsEnums = false;
        for (CodegenProperty cp : petItems.vars) {
            if ("type".equals(cp.name)) {
                typeSeen = true;
                if (null != cp.get_enum() && cp.get_enum().contains("dog") && cp.get_enum().contains("cat")) {
                    typeContainsEnums = true;
                }
                break;
            }
        }
        assertTrue(typeSeen);
        assertTrue(typeContainsEnums);

        assertEquals(
                1,
                ((StringSchema) openAPI.getComponents().getSchemas().get("Dog").getProperties().get("type")).getEnum().size()
        );
        assertEquals(
                1,
                ((StringSchema) openAPI.getComponents().getSchemas().get("Cat").getProperties().get("type")).getEnum().size()
        );
    }

    @Test
    public void testOneOfMergeProperties() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue15859.json");
        final DefaultCodegen codegen = new DefaultCodegen();
        String modelName = "Pet";

        final Schema schema = openAPI.getComponents().getSchemas().get(modelName);
        codegen.setOpenAPI(openAPI);
        CodegenModel pet = codegen.fromModel(modelName, schema);

        Set<String> oneOf = new TreeSet<>();
        oneOf.add("Dog");
        oneOf.add("Cat");
        assertEquals(pet.oneOf, oneOf);
        // make sure that animal has the property type
        boolean typeSeen = false;
        for (CodegenProperty cp : pet.vars) {
            if ("type".equals(cp.name)) {
                typeSeen = true;
                break;
            }
        }
        assertTrue(typeSeen);
    }

    @Test
    public void testExecutePostProcessor() throws InterruptedException, ExecutionException {
        final DefaultCodegen codegen = new DefaultCodegen();

        ExecutorService executor = Executors.newFixedThreadPool(1);
        try {
            Future<Boolean> call1 = executor.submit(() -> codegen.executePostProcessor(new String[]{"binary_does_not_exist"}));

            Future<Boolean> call2 = executor.submit(() -> {
                String os = System.getProperty("os.name");
                String postProcessor = os.contains("Windows")
                        ? "cmd.exe /c echo hello"
                        : "echo Hello";
                return codegen.executePostProcessor(new String[]{postProcessor});
            });

            Future<Boolean> call3 = executor.submit(() -> {
                String os = System.getProperty("os.name");
                String[] postProcessor = os.contains("Windows")
                        ? new String[]{"cmd.exe", "/c", "echo", "hello"}
                        : new String[]{"echo", "Hello"};
                return codegen.executePostProcessor(postProcessor);
            });

            assertFalse(call1.get());
            assertTrue(call2.get());
            assertTrue(call3.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testComposedSchemaOneOfWithProperties() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/oneOf.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();

        final Schema schema = openAPI.getComponents().getSchemas().get("fruit");
        codegen.setOpenAPI(openAPI);
        CodegenModel fruit = codegen.fromModel("Fruit", schema);

        Set<String> oneOf = new TreeSet<>();
        oneOf.add("Apple");
        oneOf.add("Banana");
        assertEquals(fruit.oneOf, oneOf);
        assertEquals(3, fruit.optionalVars.size());
        assertEquals(3, fruit.vars.size());
        // make sure that fruit has the property color
        boolean colorSeen = false;
        for (CodegenProperty cp : fruit.vars) {
            if ("color".equals(cp.name)) {
                colorSeen = true;
                break;
            }
        }
        assertTrue(colorSeen);
        colorSeen = false;
        for (CodegenProperty cp : fruit.optionalVars) {
            if ("color".equals(cp.name)) {
                colorSeen = true;
                break;
            }
        }
        assertTrue(colorSeen);
    }

    @Test
    public void testEscapeText() {
        final DefaultCodegen codegen = new DefaultCodegen();

        assertEquals(" ", codegen.escapeText("\n"));
        assertEquals(" ", codegen.escapeText("\r"));
        assertEquals(" ", codegen.escapeText("\t"));
        assertEquals("\\\\", codegen.escapeText("\\"));
        assertEquals("\\\"", codegen.escapeText("\""));
        assertEquals("/", codegen.escapeText("\\/"));
    }

    @Test
    public void testEscapeTextWhileAllowingNewLines() {
        final DefaultCodegen codegen = new DefaultCodegen();

        // allow new lines
        assertEquals("\n", codegen.escapeTextWhileAllowingNewLines("\n"));
        assertEquals("\r", codegen.escapeTextWhileAllowingNewLines("\r"));

        // escape other special characters
        assertEquals(" ", codegen.escapeTextWhileAllowingNewLines("\t"));
        assertEquals("\\\\", codegen.escapeTextWhileAllowingNewLines("\\"));
        assertEquals("\\\"", codegen.escapeTextWhileAllowingNewLines("\""));
        assertEquals("/", codegen.escapeTextWhileAllowingNewLines("\\/"));
    }

    @Test
    public void updateCodegenPropertyEnum() {
        final DefaultCodegen codegen = new DefaultCodegen();
        CodegenProperty array = codegenPropertyWithArrayOfIntegerValues();

        codegen.updateCodegenPropertyEnum(array);

        List<Map<String, Object>> enumVars = (List<Map<String, Object>>) array.getItems().getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Map<String, Object> testedEnumVar = enumVars.get(0);
        Assertions.assertNotNull(testedEnumVar);
        assertEquals("_1", testedEnumVar.getOrDefault("name", ""));
        assertEquals("\"1\"", testedEnumVar.getOrDefault("value", ""));
        assertEquals(false, testedEnumVar.getOrDefault("isString", ""));
    }

    @Test
    public void updateCodegenPropertyEnumWithExtension() {
        {
            CodegenProperty enumProperty = codegenPropertyWithXEnumVarName(Arrays.asList("dog", "cat"), Arrays.asList("DOGVAR", "CATVAR"));
            (new DefaultCodegen()).updateCodegenPropertyEnum(enumProperty);
            List<Map<String, Object>> enumVars = (List<Map<String, Object>>) enumProperty.getAllowableValues().get("enumVars");
            Assertions.assertNotNull(enumVars);
            Assertions.assertNotNull(enumVars.get(0));
            assertEquals("DOGVAR", enumVars.get(0).getOrDefault("name", ""));
            assertEquals("\"dog\"", enumVars.get(0).getOrDefault("value", ""));
            Assertions.assertNotNull(enumVars.get(1));
            assertEquals("CATVAR", enumVars.get(1).getOrDefault("name", ""));
            assertEquals("\"cat\"", enumVars.get(1).getOrDefault("value", ""));
        }
        {
            CodegenProperty enumProperty = codegenPropertyWithXEnumVarName(Arrays.asList("1", "2"), Arrays.asList("ONE", "TWO"));
            (new DefaultCodegen()).updateCodegenPropertyEnum(enumProperty);
            List<Map<String, Object>> enumVars = (List<Map<String, Object>>) enumProperty.getAllowableValues().get("enumVars");
            assertEquals("ONE", enumVars.get(0).getOrDefault("name", ""));
            assertEquals("\"1\"", enumVars.get(0).getOrDefault("value", ""));
            assertEquals("TWO", enumVars.get(1).getOrDefault("name", ""));
            assertEquals("\"2\"", enumVars.get(1).getOrDefault("value", ""));
        }
        {
            CodegenProperty enumProperty = codegenPropertyWithXEnumVarName(Arrays.asList("a", "b", "c", "d"), Arrays.asList("FOO", "BAR"));
            (new DefaultCodegen()).updateCodegenPropertyEnum(enumProperty);
            List<Map<String, Object>> enumVars = (List<Map<String, Object>>) enumProperty.getAllowableValues().get("enumVars");
            assertEquals("FOO", enumVars.get(0).getOrDefault("name", ""));
            assertEquals("BAR", enumVars.get(1).getOrDefault("name", ""));
            assertEquals("C", enumVars.get(2).getOrDefault("name", ""));
            assertEquals("D", enumVars.get(3).getOrDefault("name", ""));
        }
        {
            CodegenProperty enumProperty = codegenPropertyWithXEnumVarName(Arrays.asList("a", "b"), Arrays.asList("FOO", "BAR", "BAZ"));
            (new DefaultCodegen()).updateCodegenPropertyEnum(enumProperty);
            List<Map<String, Object>> enumVars = (List<Map<String, Object>>) enumProperty.getAllowableValues().get("enumVars");
            assertEquals("FOO", enumVars.get(0).getOrDefault("name", ""));
            assertEquals("BAR", enumVars.get(1).getOrDefault("name", ""));
            assertEquals(2, enumVars.size());
        }
    }

    @Test
    public void updateCodegenPropertyEnumWithPrefixRemoved() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setRemoveEnumValuePrefix(true);
        CodegenProperty enumProperty = codegenProperty(Arrays.asList("animal_dog", "animal_cat"));

        codegen.updateCodegenPropertyEnum(enumProperty);

        List<Map<String, Object>> enumVars = (List<Map<String, Object>>) enumProperty.getItems().getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Assertions.assertNotNull(enumVars.get(0));
        assertEquals("DOG", enumVars.get(0).getOrDefault("name", ""));
        assertEquals("\"animal_dog\"", enumVars.get(0).getOrDefault("value", ""));
        Assertions.assertNotNull(enumVars.get(1));
        assertEquals("CAT", enumVars.get(1).getOrDefault("name", ""));
        assertEquals("\"animal_cat\"", enumVars.get(1).getOrDefault("value", ""));
    }

    @Test
    public void updateCodegenPropertyEnumWithoutPrefixRemoved() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setRemoveEnumValuePrefix(false);

        CodegenProperty enumProperty = codegenProperty(Arrays.asList("animal_dog", "animal_cat"));

        codegen.updateCodegenPropertyEnum(enumProperty);

        List<Map<String, Object>> enumVars = (List<Map<String, Object>>) enumProperty.getItems().getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Assertions.assertNotNull(enumVars.get(0));
        assertEquals("ANIMAL_DOG", enumVars.get(0).getOrDefault("name", ""));
        assertEquals("\"animal_dog\"", enumVars.get(0).getOrDefault("value", ""));
        Assertions.assertNotNull(enumVars.get(1));
        assertEquals("ANIMAL_CAT", enumVars.get(1).getOrDefault("name", ""));
        assertEquals("\"animal_cat\"", enumVars.get(1).getOrDefault("value", ""));
    }

    @Test
    public void postProcessModelsEnumWithPrefixRemoved() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setRemoveEnumValuePrefix(true);
        ModelsMap objs = codegenModel(Arrays.asList("animal_dog", "animal_cat"));
        CodegenModel cm = objs.getModels().get(0).getModel();

        codegen.postProcessModelsEnum(objs);

        List<Map<String, Object>> enumVars = (List<Map<String, Object>>) cm.getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Assertions.assertNotNull(enumVars.get(0));
        assertEquals("DOG", enumVars.get(0).getOrDefault("name", ""));
        assertEquals("\"animal_dog\"", enumVars.get(0).getOrDefault("value", ""));
        Assertions.assertNotNull(enumVars.get(1));
        assertEquals("CAT", enumVars.get(1).getOrDefault("name", ""));
        assertEquals("\"animal_cat\"", enumVars.get(1).getOrDefault("value", ""));
    }

    @Test
    public void postProcessModelsEnumWithoutPrefixRemoved() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setRemoveEnumValuePrefix(false);
        ModelsMap objs = codegenModel(Arrays.asList("animal_dog", "animal_cat"));
        CodegenModel cm = objs.getModels().get(0).getModel();

        codegen.postProcessModelsEnum(objs);

        List<Map<String, Object>> enumVars = (List<Map<String, Object>>) cm.getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Assertions.assertNotNull(enumVars.get(0));
        assertEquals("ANIMAL_DOG", enumVars.get(0).getOrDefault("name", ""));
        assertEquals("\"animal_dog\"", enumVars.get(0).getOrDefault("value", ""));
        Assertions.assertNotNull(enumVars.get(1));
        assertEquals("ANIMAL_CAT", enumVars.get(1).getOrDefault("name", ""));
        assertEquals("\"animal_cat\"", enumVars.get(1).getOrDefault("value", ""));
    }

    @Test
    public void postProcessModelsEnumWithExtension() {
        final DefaultCodegen codegen = new DefaultCodegen();
        ModelsMap objs = codegenModelWithXEnumVarName();
        CodegenModel cm = objs.getModels().get(0).getModel();

        codegen.postProcessModelsEnum(objs);

        List<Map<String, Object>> enumVars = (List<Map<String, Object>>) cm.getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Assertions.assertNotNull(enumVars.get(0));
        assertEquals("DOGVAR", enumVars.get(0).getOrDefault("name", ""));
        assertEquals("\"dog\"", enumVars.get(0).getOrDefault("value", ""));
        assertEquals("This is a dog", enumVars.get(0).getOrDefault("enumDescription", ""));
        Assertions.assertNotNull(enumVars.get(1));
        assertEquals("CATVAR", enumVars.get(1).getOrDefault("name", ""));
        assertEquals("\"cat\"", enumVars.get(1).getOrDefault("value", ""));
        assertEquals("This is a cat", enumVars.get(1).getOrDefault("enumDescription", ""));
    }

    @Test
    public void testExample1() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/examples.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();

        Operation operation = openAPI.getPaths().get("/example1/singular").getGet();
        CodegenParameter codegenParameter = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter, operation.getParameters().get(0));

        assertEquals("example1 value", codegenParameter.example);

        Operation operation2 = openAPI.getPaths().get("/example1/plural").getGet();
        CodegenParameter codegenParameter2 = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter2, operation2.getParameters().get(0));
        assertEquals("An example1 value", codegenParameter2.example);
        // verify examples are available
        codegen.setParameterExamples(codegenParameter2, operation2.getParameters().get(0));
        assertEquals(1, codegenParameter2.examples.size());
    }

    @Test
    public void testExample2() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/examples.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();

        Operation operation = openAPI.getPaths().get("/example2/singular").getGet();
        CodegenParameter codegenParameter = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter, operation.getParameters().get(0));

        assertEquals("example2 value", codegenParameter.example);
    }

    @Test
    public void testExample3() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/examples.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();

        Operation operation = openAPI.getPaths().get("/example3/singular").getGet();
        CodegenParameter codegenParameter = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter, operation.getParameters().get(0));

        assertEquals("example3: parameter value", codegenParameter.example);

        Operation operation2 = openAPI.getPaths().get("/example3/plural").getGet();
        CodegenParameter codegenParameter2 = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter2, operation2.getParameters().get(0));

        assertEquals("An example3 value", codegenParameter2.example);
    }

    @Test
    public void testExample4() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/examples.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();

        Operation operation = openAPI.getPaths().get("/example4/singular").getPost();
        CodegenParameter codegenParameter = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter, operation.getRequestBody());

        assertEquals("example4 value", codegenParameter.example);

        Operation operation2 = openAPI.getPaths().get("/example4/plural").getPost();
        CodegenParameter codegenParameter2 = CodegenModelFactory.newInstance(CodegenModelType.PARAMETER);
        codegen.setParameterExampleValue(codegenParameter2, operation2.getRequestBody());

        assertEquals("An example4 value", codegenParameter2.example);
    }

    @Test
    public void testExample5MultipleResponses() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/examples.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        String path = "/example5/multiple_responses";

        Operation operation = openAPI.getPaths().get(path).getGet();
        CodegenOperation codegenOperation = codegen.fromOperation(path, "GET", operation, null);
        List<Map<String, String>> examples = codegenOperation.examples;

        assertEquals(4, examples.size());
        // 200 response example
        assertEquals(APP_JSON, examples.get(0).get("contentType"));
        assertEquals("\"a successful response example\"", examples.get(0).get("example"));
        assertEquals("200", examples.get(0).get("statusCode"));
        // 301 response example
        assertEquals(APP_JSON, examples.get(1).get("contentType"));
        assertEquals("\"a redirect response example\"", examples.get(1).get("example"));
        assertEquals("301", examples.get(1).get("statusCode"));
        // 404 response example
        assertEquals(APP_JSON, examples.get(2).get("contentType"));
        assertEquals("\"a not found response example\"", examples.get(2).get("example"));
        assertEquals("404", examples.get(2).get("statusCode"));
        // 500 response example
        assertEquals(APP_JSON, examples.get(3).get("contentType"));
        assertEquals("\"an internal server error response example\"", examples.get(3).get("example"));
        assertEquals("500", examples.get(3).get("statusCode"));
    }

    @Test
    public void testDiscriminator() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/petstore-with-fake-endpoints-models-for-testing.yaml");
        DefaultCodegen codegen = new DefaultCodegen();

        Schema animal = openAPI.getComponents().getSchemas().get("Animal");
        codegen.setOpenAPI(openAPI);
        CodegenModel animalModel = codegen.fromModel("Animal", animal);
        CodegenDiscriminator discriminator = animalModel.getDiscriminator();
        CodegenDiscriminator test = new CodegenDiscriminator();
        test.setPropertyName("className");
        test.setPropertyBaseName("className");
        test.getMappedModels().add(new CodegenDiscriminator.MappedModel("Dog", "Dog"));
        test.getMappedModels().add(new CodegenDiscriminator.MappedModel("Cat", "Cat"));
        test.getMappedModels().add(new CodegenDiscriminator.MappedModel("BigCat", "BigCat"));
        assertEquals(discriminator, test);
        assertTrue(animalModel.getHasDiscriminatorWithNonEmptyMapping());
    }

    @Test
    public void testDiscriminatorWithCustomMapping() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        String path = "/person/display/{personId}";
        Operation operation = openAPI.getPaths().get(path).getGet();
        CodegenOperation codegenOperation = codegen.fromOperation(path, "GET", operation, null);
        verifyPersonDiscriminator(codegenOperation.discriminator);

        Schema person = openAPI.getComponents().getSchemas().get("Person");
        codegen.setOpenAPI(openAPI);
        CodegenModel personModel = codegen.fromModel("Person", person);
        verifyPersonDiscriminator(personModel.discriminator);
        assertTrue(personModel.getHasDiscriminatorWithNonEmptyMapping());
    }

    @Test
    public void testParentName() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf.yaml");
        DefaultCodegen codegen = new DefaultCodegen();

        Schema child = openAPI.getComponents().getSchemas().get("Child");
        codegen.setOpenAPI(openAPI);
        CodegenModel childModel = codegen.fromModel("Child", child);
        assertEquals("Person", childModel.parentSchema);
        assertFalse(childModel.getHasDiscriminatorWithNonEmptyMapping());
    }

    @Test
    public void testAllOfRequired() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf-required.yaml");
        DefaultCodegen codegen = new DefaultCodegen();

        Schema child = openAPI.getComponents().getSchemas().get("clubForCreation");
        codegen.setOpenAPI(openAPI);
        CodegenModel childModel = codegen.fromModel("clubForCreation", child);
        assertEquals(getRequiredVars(childModel), Collections.singletonList("name"));
    }

    @Test
    public void testAllOfSingleAndDoubleRefWithOwnPropsNoDiscriminator() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf_composition.yaml");
        final DefaultCodegen codegen = new CodegenWithMultipleInheritance();

        codegen.setOpenAPI(openAPI);

        // to test allOf with double refs
        Schema supermanSchema = openAPI.getComponents().getSchemas().get("SuperMan");
        CodegenModel supermanModel = codegen.fromModel("SuperMan", supermanSchema);
        Assertions.assertNull(supermanModel.parent);
        Assertions.assertNull(supermanModel.allParents);

        // to test allOf with single ref
        Schema superboySchema = openAPI.getComponents().getSchemas().get("SuperBoy");
        CodegenModel superboyModel = codegen.fromModel("SuperBoy", superboySchema);
        Assertions.assertNull(superboyModel.parent);
        Assertions.assertNull(superboyModel.allParents);

        // to test allOf with single ref and no "type: object" in the (last) inline schema
        Schema superbabySchema = openAPI.getComponents().getSchemas().get("SuperBaby");
        CodegenModel superbabyModel = codegen.fromModel("SuperBaby", superbabySchema);
        Assertions.assertNull(superbabyModel.parent);
        Assertions.assertNull(superbabyModel.allParents);
    }

    @Test
    public void testAllParents() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOfMappingDuplicatedProperties.yaml");
        final DefaultCodegen codegen = new CodegenWithMultipleInheritance();

        codegen.setOpenAPI(openAPI);

        Schema adultSchema = openAPI.getComponents().getSchemas().get("Adult");
        CodegenModel adultModel = codegen.fromModel("Adult", adultSchema);
        assertEquals("Person", adultModel.parent);
        assertEquals(adultModel.allParents, Collections.singletonList("Person"));
    }

    @Test
    public void testComposedSchemaAllOfDiscriminatorMap() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf_composition_discriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setLegacyDiscriminatorBehavior(false);
        Schema sc;
        String modelName;

        String propertyName = "petType";
        String propertyBaseName = propertyName;
        CodegenDiscriminator emptyMapDisc = new CodegenDiscriminator();
        emptyMapDisc.setPropertyName(propertyName);
        emptyMapDisc.setPropertyBaseName(propertyBaseName);

        // all leaf Schemas have discriminators with PropertyName/BaseName + empty discriminator maps
        List<String> leafModelNames = Arrays.asList("Cat", "Dog", "Lizard", "Snake");
        for (String leafModelName : leafModelNames) {
            Schema leafSc = openAPI.getComponents().getSchemas().get(leafModelName);
            CodegenModel leafCm = codegen.fromModel(leafModelName, leafSc);
            assertEquals(leafCm.discriminator, emptyMapDisc);
            assertFalse(leafCm.getHasDiscriminatorWithNonEmptyMapping());
        }

        // the Pet discriminator map contains all animals + Reptile (children + grandchildren)
        CodegenDiscriminator petDisc = new CodegenDiscriminator();
        petDisc.setPropertyName(propertyName);
        petDisc.setPropertyBaseName(propertyBaseName);
        java.util.LinkedHashSet<CodegenDiscriminator.MappedModel> hs = new LinkedHashSet<>();
        for (String leafModelName : leafModelNames) {
            hs.add(new CodegenDiscriminator.MappedModel(leafModelName, codegen.toModelName(leafModelName)));
        }
        hs.add(new CodegenDiscriminator.MappedModel("Reptile", codegen.toModelName("Reptile")));
        petDisc.setMappedModels(hs);
        modelName = "Pet";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel pet = codegen.fromModel(modelName, sc);
        assertTrue(pet.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(pet.discriminator, petDisc);

        // the Reptile discriminator contains both reptiles
        List<String> reptileModelNames = Arrays.asList("Lizard", "Snake");
        CodegenDiscriminator reptileDisc = new CodegenDiscriminator();
        reptileDisc.setPropertyName(propertyName);
        reptileDisc.setPropertyBaseName(propertyBaseName);
        hs.clear();
        for (String reptileModelName : reptileModelNames) {
            hs.add(new CodegenDiscriminator.MappedModel(reptileModelName, codegen.toModelName(reptileModelName)));
        }
        reptileDisc.setMappedModels(hs);
        modelName = "Reptile";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel reptile = codegen.fromModel(modelName, sc);
        assertTrue(reptile.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(reptile.discriminator, reptileDisc);

        // the MyPets discriminator contains Cat and Lizard
        List<String> myPetNames = Arrays.asList("Cat", "Lizard");
        CodegenDiscriminator myPetDisc = new CodegenDiscriminator();
        myPetDisc.setPropertyName(propertyName);
        myPetDisc.setPropertyBaseName(propertyBaseName);
        hs.clear();
        for (String myPetName : myPetNames) {
            hs.add(new CodegenDiscriminator.MappedModel(myPetName, codegen.toModelName(myPetName)));
        }
        myPetDisc.setMappedModels(hs);
        modelName = "MyPets";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel myPets = codegen.fromModel(modelName, sc);
        assertTrue(myPets.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(myPets.discriminator, myPetDisc);

        // the MyPetsNoDisc discriminator is created because all oneOf classes have the same discriminator
        modelName = "MyPetsNoDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel myPetsNoDisc = codegen.fromModel(modelName, sc);
        assertTrue(myPetsNoDisc.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(myPetsNoDisc.discriminator, myPetDisc);

        CodegenModel cm;

        // the mapping in b is in A
        modelName = "A";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs.clear();
        hs.add(new CodegenDiscriminator.MappedModel("b", codegen.toModelName("B"), true));
        hs.add(new CodegenDiscriminator.MappedModel("C", codegen.toModelName("C")));
        assertTrue(cm.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // the mapping in b is in B
        modelName = "B";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs.clear();
        hs.add(new CodegenDiscriminator.MappedModel("b", codegen.toModelName("B"), true));
        hs.add(new CodegenDiscriminator.MappedModel("C", codegen.toModelName("C")));
        assertTrue(cm.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // the mapping in b is in C
        modelName = "C";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs.clear();
        hs.add(new CodegenDiscriminator.MappedModel("b", codegen.toModelName("B"), true));
        assertTrue(cm.getHasDiscriminatorWithNonEmptyMapping());
        assertEquals(cm.discriminator.getMappedModels(), hs);
    }

    @Test
    public void testComposedSchemaAllOfDiscriminatorMapLegacy() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf_composition_discriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        // codegen.legacyDiscriminatorBehavior remains false in the legacy use case
        codegen.setOpenAPI(openAPI);
        Schema sc;
        String modelName;

        String propertyName = "petType";
        String propertyBaseName = propertyName;
        CodegenDiscriminator emptyMapDisc = new CodegenDiscriminator();
        emptyMapDisc.setPropertyName(propertyName);
        emptyMapDisc.setPropertyBaseName(propertyBaseName);

        // all leaf Schemas have discriminators with PropertyName/BaseName + empty discriminator maps
        List<String> leafModelNames = Arrays.asList("Cat", "Dog", "Lizard", "Snake");
        for (String leafModelName : leafModelNames) {
            Schema leafSc = openAPI.getComponents().getSchemas().get(leafModelName);
            CodegenModel leafCm = codegen.fromModel(leafModelName, leafSc);
            Assertions.assertNull(leafCm.discriminator);
        }

        // the Pet discriminator map contains all animals + Reptile (children + grandchildren)
        CodegenDiscriminator petDisc = new CodegenDiscriminator();
        petDisc.setPropertyName(propertyName);
        petDisc.setPropertyBaseName(propertyBaseName);
        java.util.LinkedHashSet<CodegenDiscriminator.MappedModel> hs = new LinkedHashSet<>();
        for (String leafModelName : leafModelNames) {
            hs.add(new CodegenDiscriminator.MappedModel(leafModelName, codegen.toModelName(leafModelName)));
        }
        hs.add(new CodegenDiscriminator.MappedModel("Reptile", codegen.toModelName("Reptile")));
        petDisc.setMappedModels(hs);
        modelName = "Pet";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel pet = codegen.fromModel(modelName, sc);
        assertEquals(pet.discriminator, petDisc);

        // the Reptile discriminator contains both reptiles
        List<String> reptileModelNames = Arrays.asList("Lizard", "Snake");
        CodegenDiscriminator reptileDisc = new CodegenDiscriminator();
        reptileDisc.setPropertyName(propertyName);
        reptileDisc.setPropertyBaseName(propertyBaseName);
        hs.clear();
        for (String reptileModelName : reptileModelNames) {
            hs.add(new CodegenDiscriminator.MappedModel(reptileModelName, codegen.toModelName(reptileModelName)));
        }
        reptileDisc.setMappedModels(hs);
        modelName = "Reptile";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel reptile = codegen.fromModel(modelName, sc);
        Assertions.assertNull(reptile.discriminator);

        // the MyPets discriminator contains Cat and Lizard
        CodegenDiscriminator myPetDisc = new CodegenDiscriminator();
        myPetDisc.setPropertyName(propertyName);
        myPetDisc.setPropertyBaseName(propertyBaseName);
        hs.clear();
        modelName = "MyPets";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel myPets = codegen.fromModel(modelName, sc);
        assertEquals(myPets.discriminator, myPetDisc);

        // the MyPetsNoDisc discriminator is created because all oneOf classes have the same discriminator
        modelName = "MyPetsNoDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel myPetsNoDisc = codegen.fromModel(modelName, sc);
        Assertions.assertNull(myPetsNoDisc.discriminator);

        CodegenModel cm;

        // the mapping in b is in A
        modelName = "A";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs.clear();
        hs.add(new CodegenDiscriminator.MappedModel("b", codegen.toModelName("B"), true));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // the mapping in b is in B
        modelName = "B";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        Assertions.assertNull(cm.discriminator);

        // the mapping in b is in C
        modelName = "C";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        Assertions.assertNull(cm.discriminator);
    }

    @Test
    public void testComposedSchemaOneOfDiscriminatorsInvalid() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/oneOfDiscriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        HashMap<String, String> hm = new HashMap<>();
        hm.put("ComposedDiscMissingNoProperties", "'ComposedDiscMissingNoProperties' defines discriminator 'fruitType', but the referenced schema 'DiscMissingNoProperties' is incorrect. fruitType is missing from the schema, define it as required and type string");
        hm.put("ComposedDiscMissingFromProperties", "'ComposedDiscMissingFromProperties' defines discriminator 'fruitType', but the referenced schema 'DiscMissingFromProperties' is incorrect. fruitType is missing from the schema, define it as required and type string");
        hm.put("ComposedDiscOptionalTypeCorrect", "'ComposedDiscOptionalTypeCorrect' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeCorrect' is incorrect. invalid optional definition of fruitType, include it in required");
        hm.put("ComposedDiscOptionalTypeIncorrect", "'ComposedDiscOptionalTypeIncorrect' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeIncorrect' is incorrect. invalid type for fruitType, set it to string. invalid optional definition of fruitType, include it in required");
        hm.put("ComposedDiscOptionalTypeInconsistent", "'ComposedDiscOptionalTypeInconsistent' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeIncorrect' is incorrect. invalid type for fruitType, set it to string. invalid optional definition of fruitType, include it in required");
        hm.put("ComposedDiscTypeIncorrect", "'ComposedDiscTypeIncorrect' defines discriminator 'fruitType', but the referenced schema 'DiscTypeIncorrect' is incorrect. invalid type for fruitType, set it to string");
        hm.put("ComposedDiscTypeInconsistent", "'ComposedDiscTypeInconsistent' defines discriminator 'fruitType', but the referenced schema 'DiscTypeIncorrect' is incorrect. invalid type for fruitType, set it to string");
        hm.put("ComposedDiscRequiredInconsistent", "'ComposedDiscRequiredInconsistent' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeCorrect' is incorrect. invalid optional definition of fruitType, include it in required");

        for (Map.Entry<String, String> entry : hm.entrySet()) {
            String modelName = entry.getKey();
            String errorMessageExpected = entry.getValue();

            Schema sc = openAPI.getComponents().getSchemas().get(modelName);

            /*
            // comment out below as we're now showing warnings instead of throwing exceptions
            try {
                codegen.fromModel(modelName, sc);
                Assertions.assertTrue(false, "A RuntimeException should have been thrown when processing "+modelName+ " but it was not");
            } catch (RuntimeException re) {
                Assertions.assertEquals(re.getMessage(), errorMessageExpected);
            }
            */
        }
    }

    @Test
    public void testComposedSchemaAnyOfDiscriminatorsInvalid() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/anyOfDiscriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        HashMap<String, String> hm = new HashMap<>();
        hm.put("ComposedDiscMissingNoProperties", "'ComposedDiscMissingNoProperties' defines discriminator 'fruitType', but the referenced schema 'DiscMissingNoProperties' is incorrect. fruitType is missing from the schema, define it as required and type string");
        hm.put("ComposedDiscMissingFromProperties", "'ComposedDiscMissingFromProperties' defines discriminator 'fruitType', but the referenced schema 'DiscMissingFromProperties' is incorrect. fruitType is missing from the schema, define it as required and type string");
        hm.put("ComposedDiscOptionalTypeCorrect", "'ComposedDiscOptionalTypeCorrect' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeCorrect' is incorrect. invalid optional definition of fruitType, include it in required");
        hm.put("ComposedDiscOptionalTypeIncorrect", "'ComposedDiscOptionalTypeIncorrect' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeIncorrect' is incorrect. invalid type for fruitType, set it to string. invalid optional definition of fruitType, include it in required");
        hm.put("ComposedDiscOptionalTypeInconsistent", "'ComposedDiscOptionalTypeInconsistent' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeIncorrect' is incorrect. invalid type for fruitType, set it to string. invalid optional definition of fruitType, include it in required");
        hm.put("ComposedDiscTypeIncorrect", "'ComposedDiscTypeIncorrect' defines discriminator 'fruitType', but the referenced schema 'DiscTypeIncorrect' is incorrect. invalid type for fruitType, set it to string");
        hm.put("ComposedDiscTypeInconsistent", "'ComposedDiscTypeInconsistent' defines discriminator 'fruitType', but the referenced schema 'DiscTypeIncorrect' is incorrect. invalid type for fruitType, set it to string");
        hm.put("ComposedDiscRequiredInconsistent", "'ComposedDiscRequiredInconsistent' defines discriminator 'fruitType', but the referenced schema 'DiscOptionalTypeCorrect' is incorrect. invalid optional definition of fruitType, include it in required");

        for (Map.Entry<String, String> entry : hm.entrySet()) {
            String modelName = entry.getKey();
            String errorMessageExpected = entry.getValue();

            Schema sc = openAPI.getComponents().getSchemas().get(modelName);

            /*
            // comment out below as we're now showing warnings instead of throwing exceptions
            try {
                codegen.fromModel(modelName, sc);
                Assertions.assertTrue(false, "A RuntimeException should have been thrown when processing "+modelName+ " but it was not");
            } catch (RuntimeException re) {
                Assertions.assertEquals(re.getMessage(), errorMessageExpected);
            }
            */
        }
    }

    @Test
    public void testComposedSchemaAnyOfDiscriminatorMap() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/anyOfDiscriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        String modelName;
        Schema sc;
        CodegenModel cm;
        java.util.LinkedHashSet<CodegenDiscriminator.MappedModel> hs;
        String mn;

        // inline anyOf models work because the inline schemas are turned into $refs
        modelName = "FruitInlineDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "FruitInlineDisc_anyOf";
        hs.add(new CodegenDiscriminator.MappedModel(mn, codegen.toModelName(mn)));
        mn = "FruitInlineDisc_anyOf_1";
        hs.add(new CodegenDiscriminator.MappedModel(mn, codegen.toModelName(mn)));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // inline anyOf with inline anyOf model doesn't work because we have null $refs and we throw an exception
        final String fmodelName = "FruitInlineInlineDisc";
        final Schema fsc = openAPI.getComponents().getSchemas().get(fmodelName);
        // comment out below as we're now showing warnings instead of throwing exceptions
        //Assertions.assertThrows(() -> codegen.fromModel(fmodelName, fsc));

        // ref anyOf models with discriminator in properties in those models
        modelName = "FruitReqDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleReqDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaReqDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in allOf in those models
        modelName = "FruitAllOfDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleAllOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaAllOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in anyOf in those models
        modelName = "FruitAnyOfDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleAnyOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaAnyOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in anyOf in those models
        modelName = "FruitAnyOfDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleAnyOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaAnyOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in the grandparent schemas of those anyof models
        modelName = "FruitGrandparentDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleGrandparentDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaGrandparentDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);
    }

    @Test
    public void testComposedSchemaOneOfDiscriminatorMap() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/oneOfDiscriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        String modelName;
        Schema sc;
        CodegenModel cm;
        java.util.LinkedHashSet<CodegenDiscriminator.MappedModel> hs;
        String mn;

        // inline oneOf models work because the inline schemas are turned into $refs
        modelName = "FruitInlineDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "FruitInlineDisc_oneOf";
        hs.add(new CodegenDiscriminator.MappedModel(mn, codegen.toModelName(mn)));
        mn = "FruitInlineDisc_oneOf_1";
        hs.add(new CodegenDiscriminator.MappedModel(mn, codegen.toModelName(mn)));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // inline oneOf with inline oneOf model doesn't work because we have null $refs and we throw an exception
        final String fmodelName = "FruitInlineInlineDisc";
        final Schema fsc = openAPI.getComponents().getSchemas().get(fmodelName);
        // comment out below as we're now showing warnings instead of throwing exceptions
        //Assertions.assertThrows(() -> codegen.fromModel(fmodelName, fsc));

        // ref oneOf models with discriminator in properties in those models
        modelName = "FruitReqDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleReqDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaReqDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in allOf in those models
        modelName = "FruitAllOfDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleAllOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaAllOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in anyOf in those models
        modelName = "FruitAnyOfDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleAnyOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaAnyOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with discriminator in oneOf in those models
        modelName = "FruitOneOfDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleOneOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaOneOfDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);

        // ref oneOf models with enum property discriminator
        modelName = "FruitOneOfEnumMappingDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertThat(cm.discriminator.getPropertyType()).isEqualTo("FruitTypeEnum");

        // ref oneOf models with discriminator in the grandparent schemas of those oneof models
        modelName = "FruitGrandparentDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        hs = new java.util.LinkedHashSet<>();
        mn = "AppleGrandparentDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        mn = "BananaGrandparentDisc";
        hs.add(new CodegenDiscriminator.MappedModel(mn, mn));
        assertEquals(cm.discriminator.getMappedModels(), hs);
    }

    @Test
    public void testComposedSchemaOneOfDiscriminatorMapPreAndPostFix() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/oneOfDiscriminator.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);
        codegen.setModelNamePrefix("Transfer");
        codegen.setModelNameSuffix("Dto");
        String modelName;
        Schema sc;
        CodegenModel cm;

        // ref oneOf models with enum property discriminator
        modelName = "FruitOneOfEnumMappingDisc";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertThat(cm.discriminator.getPropertyType()).isEqualTo("TransferFruitTypeEnumDto");
    }

    @Test
    public void testComposedSchemaMyPetsOneOfDiscriminatorMap() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf_composition_discriminator.yaml");

        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        String path = "/mypets";

        Operation operation = openAPI.getPaths().get(path).getGet();
        CodegenOperation codegenOperation = codegen.fromOperation(path, "GET", operation, null);
        verifyMyPetsDiscriminator(codegenOperation.discriminator);

        Schema pet = openAPI.getComponents().getSchemas().get("MyPets");
        CodegenModel petModel = codegen.fromModel("MyPets", pet);
        verifyMyPetsDiscriminator(petModel.discriminator);
    }

    @Test
    public void testComposedSchemaAllOfHierarchy() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf_composition_discriminator.yaml");

        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setOpenAPI(openAPI);

        Schema pet = openAPI.getComponents().getSchemas().get("Lizard");
        CodegenModel petModel = codegen.fromModel("Lizard", pet);
        verifyLizardDiscriminator(petModel.discriminator);

        pet = openAPI.getComponents().getSchemas().get("Reptile");
        petModel = codegen.fromModel("Reptile", pet);
        verifyReptileDiscriminator(petModel.discriminator);
    }

    private void verifyLizardDiscriminator(CodegenDiscriminator discriminator) {
        CodegenDiscriminator test = new CodegenDiscriminator();
        String prop = "petType";
        test.setPropertyName(prop);
        test.setPropertyBaseName(prop);
        test.setMapping(null);
        test.setMappedModels(new HashSet<>());
        assertEquals(discriminator, test);
    }

    private void verifyReptileDiscriminator(CodegenDiscriminator discriminator) {
        CodegenDiscriminator test = new CodegenDiscriminator();
        String prop = "petType";
        test.setPropertyName(prop);
        test.setPropertyBaseName(prop);
        test.setMapping(null);
        test.setMappedModels(new HashSet<>() {{
            add(new CodegenDiscriminator.MappedModel("Snake", "Snake"));
            add(new CodegenDiscriminator.MappedModel("Lizard", "Lizard"));
        }});
        assertEquals(discriminator, test);
    }

    private void verifyMyPetsDiscriminator(CodegenDiscriminator discriminator) {
        CodegenDiscriminator test = new CodegenDiscriminator();
        String prop = "petType";
        test.setPropertyName(prop);
        test.setPropertyBaseName(prop);
        test.setMapping(null);
        test.setMappedModels(new HashSet<>() {{
            add(new CodegenDiscriminator.MappedModel("Cat", "Cat"));
            add(new CodegenDiscriminator.MappedModel("Lizard", "Lizard"));
        }});
        assertEquals(discriminator, test);
    }

    public CodegenModel getModel(List<ModelMap> allModels, String modelName) {
        for (ModelMap obj : allModels) {
            CodegenModel cm = obj.getModel();
            if (modelName.equals(cm.name)) {
                return cm;
            }
        }
        return null;
    }

    @Test
    public void verifyXDiscriminatorValue() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/x-discriminator-value.yaml");
        final DefaultCodegen config = new DefaultCodegen();
        config.setOpenAPI(openAPI);

        String modelName;
        CodegenDiscriminator discriminator;
        CodegenModel cm;

        Boolean dryRun = Boolean.TRUE;
        final DefaultGenerator generator = new DefaultGenerator(dryRun);
        generator.openAPI = openAPI;
        generator.config = config;
        generator.configureGeneratorProperties();

        // for us to check a model's children we need to run generator.generateModels
        // because children are assigned in config.updateAllModels which is invoked in generator.generateModels
        List<File> files = new ArrayList<>();
        List<String> filteredSchemas = ModelUtils.getSchemasUsedOnlyInFormParam(openAPI);
        List<ModelMap> allModels = new ArrayList<>();
        List<ModelMap> aliasModels = new ArrayList<>();
        generator.generateModels(files, allModels, filteredSchemas, aliasModels);

        // check that the model's children contain the x-discriminator-values
        modelName = "BaseObj";
        cm = getModel(allModels, modelName);
        Assertions.assertNotNull(cm);
        Assertions.assertNotNull(cm.children);
        List<String> expectedDiscriminatorValues = new ArrayList<>(Arrays.asList("daily", "sub-obj"));
        ArrayList<String> xDiscriminatorValues = new ArrayList<>();
        for (CodegenModel child : cm.children) {
            xDiscriminatorValues.add((String) child.vendorExtensions.get("x-discriminator-value"));
        }
        assertEquals(xDiscriminatorValues, expectedDiscriminatorValues);

        // check that the discriminator's MappedModels also contains the x-discriminator-values
        discriminator = new CodegenDiscriminator();
        String prop = "object_type";
        discriminator.setPropertyName(config.toVarName(prop));
        discriminator.setPropertyBaseName(prop);
        discriminator.setMapping(null);
        discriminator.setMappedModels(new HashSet<>() {{
            add(new CodegenDiscriminator.MappedModel("daily", "DailySubObj", true));
            add(new CodegenDiscriminator.MappedModel("sub-obj", "SubObj", true));
        }});
        assertEquals(cm.discriminator, discriminator);
    }

    @Test
    public void testAllOfSingleRefNoOwnProps() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/composed-allof.yaml");
        final DefaultCodegen codegen = new CodegenWithMultipleInheritance();

        Schema schema = openAPI.getComponents().getSchemas().get("NewMessageEventCoreNoOwnProps");
        codegen.setOpenAPI(openAPI);
        CodegenModel model = codegen.fromModel("NewMessageEventCoreNoOwnProps", schema);
        assertEquals(getNames(model.getVars()), Arrays.asList("id", "message"));
        Assertions.assertNull(model.parent);
        Assertions.assertNull(model.allParents);
    }

    @Test
    public void testAllOfParent() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf-required-parent.yaml");
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Schema person = openAPI.getComponents().getSchemas().get("person");
        CodegenModel personModel = codegen.fromModel("person", person);
        assertEquals(getRequiredVars(personModel), Arrays.asList("firstName", "name", "email", "id"));

        Schema personForCreation = openAPI.getComponents().getSchemas().get("personForCreation");
        CodegenModel personForCreationModel = codegen.fromModel("personForCreation", personForCreation);
        assertEquals(getRequiredVars(personForCreationModel), Arrays.asList("firstName", "name", "email"));

        Schema personForUpdate = openAPI.getComponents().getSchemas().get("personForUpdate");
        CodegenModel personForUpdateModel = codegen.fromModel("personForUpdate", personForUpdate);
        assertEquals(getRequiredVars(personForUpdateModel), Collections.emptyList());
    }

    private List<String> getRequiredVars(CodegenModel model) {
        return getNames(model.getRequiredVars());
    }

    private List<String> getNames(List<CodegenProperty> props) {
        if (props == null) return null;
        return props.stream().map(v -> v.name).collect(Collectors.toList());
    }

    @Test
    public void testCallbacks() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/callbacks.yaml");
        final CodegenConfig codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final String path = "/streams";
        Operation subscriptionOperation = openAPI.getPaths().get("/streams").getPost();
        CodegenOperation op = codegen.fromOperation(path, "post", subscriptionOperation, null);

        assertFalse(op.isCallbackRequest);
        Assertions.assertNotNull(op.operationId);
        assertEquals(2, op.callbacks.size());

        CodegenCallback cbB = op.callbacks.get(1);
        assertEquals("dummy", cbB.name);
        assertEquals(0, cbB.urls.size());

        CodegenCallback cbA = op.callbacks.get(0);
        assertEquals("onData", cbA.name);

        assertEquals(2, cbA.urls.size());

        CodegenCallback.Url urlB = cbA.urls.get(1);
        assertEquals("{$request.query.callbackUrl}/test", urlB.expression);
        assertEquals(0, urlB.requests.size());

        CodegenCallback.Url urlA = cbA.urls.get(0);
        assertEquals("{$request.query.callbackUrl}/data", urlA.expression);
        assertEquals(2, urlA.requests.size());

        urlA.requests.forEach(req -> {
            assertTrue(req.isCallbackRequest);
            Assertions.assertNotNull(req.bodyParam);
            assertEquals(2, req.responses.size());

            switch (req.httpMethod.toLowerCase(Locale.getDefault())) {
                case "post":
                    assertEquals("onDataDataPost", req.operationId);
                    assertEquals("NewNotificationData", req.bodyParam.dataType);
                    break;
                case "delete":
                    assertEquals("onDataDataDelete", req.operationId);
                    assertEquals("DeleteNotificationData", req.bodyParam.dataType);
                    break;
                default:
                    Assert.fail(String.format(Locale.getDefault(), "invalid callback request http method '%s'", req.httpMethod));
            }
        });
    }

    @Test
    public void testLeadingSlashIsAddedIfMissing() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        Operation operation1 = new Operation().operationId("op1").responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")));
        openAPI.path("/here", new PathItem().get(operation1));
        Operation operation2 = new Operation().operationId("op2").responses(new ApiResponses().addApiResponse("201", new ApiResponse().description("OK")));
        openAPI.path("some/path", new PathItem().get(operation2));
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenOperation co1 = codegen.fromOperation("/here", "get", operation2, null);
        assertEquals("/here", co1.path);
        CodegenOperation co2 = codegen.fromOperation("some/path", "get", operation2, null);
        assertEquals("/some/path", co2.path);
    }

    @Test
    public void testDefaultResponseShouldBeLast() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        Operation myOperation = new Operation().operationId("myOperation").responses(
                new ApiResponses()
                        .addApiResponse(
                                "default", new ApiResponse().description("Default"))
                        .addApiResponse(
                                "422", new ApiResponse().description("Error"))
        );
        openAPI.path("/here", new PathItem().get(myOperation));
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenOperation co = codegen.fromOperation("/here", "get", myOperation, null);
        assertEquals("Error", co.responses.get(0).message);
        assertEquals("Default", co.responses.get(1).message);
    }

    @Test
    public void testResponseWithNoSchemaInHeaders() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        ApiResponse response2XX = new ApiResponse()
                .description("OK")
                .addHeaderObject("x-custom-header", new Header()
                        .description("a custom header")
                        .style(Header.StyleEnum.SIMPLE));
        Operation operation1 = new Operation().operationId("op1").responses(new ApiResponses().addApiResponse("2XX", response2XX));
        openAPI.path("/here", new PathItem().get(operation1));
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenResponse cr = codegen.fromResponse("2XX", response2XX);
        Assertions.assertNotNull(cr);
        assertTrue(cr.hasHeaders);
    }

    @Test
    public void testNullableProperty() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/examples.yaml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenProperty property = codegen.fromProperty("address", (Schema) openAPI.getComponents().getSchemas().get("User").getProperties().get("address"));

        assertTrue(property.isNullable);
    }

    @Test
    public void testDeprecatedModel() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/component-deprecated.yml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenModel codegenPetModel = codegen.fromModel("Pet", openAPI.getComponents().getSchemas().get("Pet"));
        assertTrue(codegenPetModel.isDeprecated);

        CodegenModel codegenFoodModel = codegen.fromModel("Food", openAPI.getComponents().getSchemas().get("Food"));
        assertTrue(codegenFoodModel.isDeprecated);
    }

    @Test
    public void testDeprecatedProperty() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/property-deprecated.yaml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final Map responseProperties = Collections.unmodifiableMap(openAPI.getComponents().getSchemas().get("Response").getProperties());
        final Map requestProperties = Collections.unmodifiableMap(openAPI.getComponents().getSchemas().get("Response").getProperties());

        assertTrue(codegen.fromProperty("firstName", (Schema) responseProperties.get("firstName")).deprecated);
        assertFalse(codegen.fromProperty("customerCode", (Schema) responseProperties.get("customerCode")).deprecated);
        assertTrue(codegen.fromProperty("firstName", (Schema) requestProperties.get("firstName")).deprecated);
        assertFalse(codegen.fromProperty("customerCode", (Schema) requestProperties.get("customerCode")).deprecated);
    }

    @Test
    public void testTitleProperty() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/property-title.yaml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final Map testProperties = Collections.unmodifiableMap(openAPI.getComponents().getSchemas().get("ModelWithTitledProperties").getProperties());

        assertEquals("Simple-Property-Title", codegen.fromProperty("simpleProperty", (Schema) testProperties.get("simpleProperty")).title);
        assertEquals("Ref-Property-Title", codegen.fromProperty("refProperty", (Schema) testProperties.get("refProperty")).title);
    }

    @Test
    public void testDeprecatedRef() {
        final OpenAPI openAPI = TestUtils.parseSpec("src/test/resources/3_0/model-deprecated.yaml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final Map requestProperties = Collections.unmodifiableMap(openAPI.getComponents().getSchemas().get("complex").getProperties());

        assertTrue(codegen.fromProperty("deprecated", (Schema) requestProperties.get("deprecated")).deprecated);
        assertFalse(codegen.fromProperty("current", (Schema) requestProperties.get("current")).deprecated);
    }

    @Test
    public void integerSchemaPropertyAndModelTest() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        final Schema schema = new IntegerSchema().format("int32");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        //Property:
        final CodegenProperty cp = codegen.fromProperty("someProperty", schema);
        assertEquals("integer", cp.baseType);
        assertEquals("someProperty", cp.baseName);
        assertFalse(cp.isString);
        assertTrue(cp.isInteger);
        assertFalse(cp.isLong);
        assertFalse(cp.isNumber);
        assertTrue(cp.isNumeric);
        assertFalse(cp.isFloat);
        assertFalse(cp.isDouble);

        //Model:
        final CodegenModel cm = codegen.fromModel("someModel", schema);
        assertEquals("integer", cm.dataType);
        assertEquals("someModel", cm.name);
        assertFalse(cm.isString);
        assertTrue(cm.isInteger);
        assertFalse(cm.isLong);
        assertFalse(cm.isNumber);
        assertTrue(cm.isNumeric);
        assertFalse(cm.isFloat);
        assertFalse(cm.isDouble);
    }

    @Test
    public void longSchemaPropertyAndModelTest() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        final Schema schema = new IntegerSchema().format("int64");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        //Property:
        final CodegenProperty cp = codegen.fromProperty("someProperty", schema);
        assertEquals("long", cp.baseType);
        assertEquals("someProperty", cp.baseName);
        assertFalse(cp.isString);
        assertFalse(cp.isInteger);
        assertTrue(cp.isLong);
        assertFalse(cp.isNumber);
        assertTrue(cp.isNumeric);
        assertFalse(cp.isFloat);
        assertFalse(cp.isDouble);

        //Model:
        final CodegenModel cm = codegen.fromModel("someModel", schema);
        assertEquals("long", cm.dataType);
        assertEquals("someModel", cm.name);
        assertFalse(cm.isString);
        assertFalse(cm.isInteger);
        assertTrue(cm.isLong);
        assertFalse(cm.isNumber);
        assertTrue(cm.isNumeric);
        assertFalse(cm.isFloat);
        assertFalse(cm.isDouble);
    }

    @Test
    public void numberSchemaPropertyAndModelTest() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        final Schema schema = new NumberSchema();
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        //Property:
        final CodegenProperty cp = codegen.fromProperty("someProperty", schema);
        assertEquals("number", cp.baseType);
        assertEquals("someProperty", cp.baseName);
        assertFalse(cp.isString);
        assertFalse(cp.isInteger);
        assertFalse(cp.isLong);
        assertTrue(cp.isNumber);
        assertTrue(cp.isNumeric);
        assertFalse(cp.isFloat);
        assertFalse(cp.isDouble);

        //Model:
        final CodegenModel cm = codegen.fromModel("someModel", schema);
        assertEquals("number", cm.dataType);
        assertEquals("someModel", cm.name);
        assertFalse(cm.isString);
        assertFalse(cm.isInteger);
        assertFalse(cm.isLong);
        assertTrue(cm.isNumber);
        assertTrue(cm.isNumeric);
        assertFalse(cm.isFloat);
        assertFalse(cm.isDouble);
    }

    @Test
    public void numberFloatSchemaPropertyAndModelTest() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        final Schema schema = new NumberSchema().format("float");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        //Property:
        final CodegenProperty cp = codegen.fromProperty("someProperty", schema);
        assertEquals("float", cp.baseType);
        assertEquals("someProperty", cp.baseName);
        assertFalse(cp.isString);
        assertFalse(cp.isInteger);
        assertFalse(cp.isLong);
        assertFalse(cp.isNumber);
        assertTrue(cp.isNumeric);
        assertTrue(cp.isFloat);
        assertFalse(cp.isDouble);

        //Model:
        final CodegenModel cm = codegen.fromModel("someModel", schema);
        assertEquals("float", cm.dataType);
        assertEquals("someModel", cm.name);
        assertFalse(cm.isString);
        assertFalse(cm.isInteger);
        assertFalse(cm.isLong);
        assertFalse(cm.isNumber);
        assertTrue(cm.isNumeric);
        assertTrue(cm.isFloat);
        assertFalse(cm.isDouble);
    }

    @Test
    public void numberDoubleSchemaPropertyAndModelTest() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        final Schema schema = new NumberSchema().format("double");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        //Property:
        final CodegenProperty cp = codegen.fromProperty("someProperty", schema);
        assertEquals("double", cp.baseType);
        assertEquals("someProperty", cp.baseName);
        assertFalse(cp.isString);
        assertFalse(cp.isInteger);
        assertFalse(cp.isLong);
        assertFalse(cp.isNumber);
        assertTrue(cp.isNumeric);
        assertFalse(cp.isFloat);
        assertTrue(cp.isDouble);

        //Model:
        final CodegenModel cm = codegen.fromModel("someModel", schema);
        assertEquals("double", cm.dataType);
        assertEquals("someModel", cm.name);
        assertFalse(cm.isString);
        assertFalse(cm.isInteger);
        assertFalse(cm.isLong);
        assertFalse(cm.isNumber);
        assertTrue(cm.isNumeric);
        assertFalse(cm.isFloat);
        assertTrue(cm.isDouble);
    }

    @Test
    public void testAlias() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/type_alias.yaml");
        new InlineModelResolver().flatten(openAPI);

        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenModel typeAliasModel = codegen.fromModel(
                "MyParameterTextField",
                openAPI.getComponents().getSchemas().get("MyParameterTextField")
        );
        assertTrue(typeAliasModel.isAlias);
        assertEquals("string", typeAliasModel.dataType);

        CodegenModel composedModel = codegen.fromModel(
                "ComposedModel",
                openAPI.getComponents().getSchemas().get("ComposedModel")
        );
        assertFalse(composedModel.isAlias);
    }

    private void verifyPersonDiscriminator(CodegenDiscriminator discriminator) {
        CodegenDiscriminator test = new CodegenDiscriminator();
        test.setPropertyName("DollarUnderscoretype");
        test.setPropertyBaseName("$_type");
        test.setMapping(new HashMap<>());
        test.getMapping().put("a", "#/components/schemas/Adult");
        test.getMapping().put("c", "Child");
        test.getMappedModels().add(new CodegenDiscriminator.MappedModel("a", "Adult", true));
        test.getMappedModels().add(new CodegenDiscriminator.MappedModel("c", "Child", true));
        assertEquals(discriminator, test);
    }

    private CodegenProperty codegenPropertyWithArrayOfIntegerValues() {
        CodegenProperty array = new CodegenProperty();
        final CodegenProperty items = new CodegenProperty();
        final HashMap<String, Object> allowableValues = new HashMap<>();
        allowableValues.put("values", Collections.singletonList(1));
        items.setAllowableValues(allowableValues);
        items.dataType = "Integer";
        array.items = items;
        array.mostInnerItems = items;
        array.dataType = "Array";
        return array;
    }

    private CodegenProperty codegenProperty(List<String> values) {
        CodegenProperty array = new CodegenProperty();
        final CodegenProperty items = new CodegenProperty();
        final HashMap<String, Object> allowableValues = new HashMap<>();
        allowableValues.put("values", values);
        items.setAllowableValues(allowableValues);
        items.dataType = "String";
        array.items = items;
        array.mostInnerItems = items;
        array.dataType = "Array";
        return array;
    }

    private CodegenProperty codegenPropertyWithXEnumVarName(List<String> values, List<String> aliases) {
        final CodegenProperty var = new CodegenProperty();
        final HashMap<String, Object> allowableValues = new HashMap<>();
        allowableValues.put("values", values);
        var.setAllowableValues(allowableValues);
        var.dataType = "String";
        Map<String, Object> extensions = Collections.singletonMap("x-enum-varnames", aliases);
        var.setVendorExtensions(extensions);
        return var;
    }

    private ModelsMap codegenModel(List<String> values) {
        final CodegenModel cm = new CodegenModel();
        cm.isEnum = true;
        final HashMap<String, Object> allowableValues = new HashMap<>();
        allowableValues.put("values", values);
        cm.setAllowableValues(allowableValues);
        cm.dataType = "String";
        return TestUtils.createCodegenModelWrapper(cm);
    }

    private ModelsMap codegenModelWithXEnumVarName() {
        final CodegenModel cm = new CodegenModel();
        cm.isEnum = true;
        final HashMap<String, Object> allowableValues = new HashMap<>();
        allowableValues.put("values", Arrays.asList("dog", "cat"));
        cm.setAllowableValues(allowableValues);
        cm.dataType = "String";
        final List<String> aliases = Arrays.asList("DOGVAR", "CATVAR");
        final List<String> descriptions = Arrays.asList("This is a dog", "This is a cat");
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("x-enum-varnames", aliases);
        extensions.put("x-enum-descriptions", descriptions);
        cm.setVendorExtensions(extensions);
        cm.setVars(Collections.emptyList());
        return TestUtils.createCodegenModelWrapper(cm);
    }

    @Test
    public void objectQueryParamIdentifyAsObject() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/objectQueryParam.yaml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Set<String> imports = new HashSet<>();
        CodegenParameter parameter = codegen.fromParameter(openAPI.getPaths().get("/pony").getGet().getParameters().get(0), imports);

        // TODO: This must be updated to work with flattened inline models
        assertEquals("ListPageQueryParameter", parameter.dataType);
        assertEquals(1, imports.size());
        assertEquals("ListPageQueryParameter", imports.iterator().next());

        Assertions.assertNotNull(parameter.getSchema());
        assertEquals("Object", parameter.getSchema().dataType);
        assertEquals("object", parameter.getSchema().baseType);
    }

    @Test
    public void mapParamImportInnerObject() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/mapArgs.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        RequestBody requestBody = openAPI.getPaths().get("/api/instruments").getPost().getRequestBody();

        HashSet<String> imports = new HashSet<>();
        codegen.fromRequestBody(requestBody, imports, "");

        HashSet<String> expected = Sets.newHashSet("InstrumentDefinition", "map");

        assertEquals(imports, expected);
    }

    @Test
    public void modelDoNotContainInheritedVars() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.supportsInheritance = true;

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/generic.yaml");
        codegen.setOpenAPI(openAPI);

        CodegenModel codegenModel = codegen.fromModel("Dog", openAPI.getComponents().getSchemas().get("Dog"));

        assertEquals(1, codegenModel.vars.size());
    }

    @Test
    public void importMapping() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.importMapping.put("TypeAlias", "foo.bar.TypeAlias");

        OpenAPI openAPI = new OpenAPIParser()
                .readLocation("src/test/resources/3_0/type-alias.yaml", null, new ParseOptions()).getOpenAPI();
        codegen.setOpenAPI(openAPI);

        CodegenModel codegenModel = codegen.fromModel("ParentType", openAPI.getComponents().getSchemas().get("ParentType"));

        assertEquals(1, codegenModel.vars.size());
        assertEquals("string", codegenModel.vars.get(0).getBaseType());
    }

    @Test
    public void schemaMapping() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.schemaMapping.put("TypeAlias", "foo.bar.TypeAlias");

        OpenAPI openAPI = new OpenAPIParser()
                .readLocation("src/test/resources/3_0/type-alias.yaml", null, new ParseOptions()).getOpenAPI();
        codegen.setOpenAPI(openAPI);

        CodegenModel codegenModel = codegen.fromModel("ParentType", openAPI.getComponents().getSchemas().get("ParentType"));

        assertEquals(1, codegenModel.vars.size());
        assertEquals("TypeAlias", codegenModel.vars.get(0).getBaseType());
    }

    @Test
    public void operationIdNameMapping() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.operationIdNameMapping.put("edge case !@# 123", "fix_edge_case");

        OpenAPI openAPI = new OpenAPIParser()
                .readLocation("src/test/resources/3_0/type-alias.yaml", null, new ParseOptions()).getOpenAPI();
        codegen.setOpenAPI(openAPI);

        CodegenOperation codegenOperation = codegen.fromOperation("/type-alias", "get", openAPI.getPaths().get("/type-alias").getGet(), null);
        assertEquals("fix_edge_case", codegenOperation.operationId);
        assertEquals("fix_edge_case", codegen.getOrGenerateOperationId(openAPI.getPaths().get("/type-alias").getGet(), "/type-alias", "get"));
    }

    @Test
    public void modelWithPrefixDoNotContainInheritedVars() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.supportsInheritance = true;
        codegen.setModelNamePrefix("prefix");

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/generic.yaml");
        codegen.setOpenAPI(openAPI);

        CodegenModel codegenModel = codegen.fromModel("Dog", openAPI.getComponents().getSchemas().get("Dog"));

        assertEquals(1, codegenModel.vars.size());
    }

    @Test
    public void modelWithSuffixDoNotContainInheritedVars() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.supportsInheritance = true;
        codegen.setModelNameSuffix("suffix");

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/generic.yaml");
        codegen.setOpenAPI(openAPI);

        CodegenModel codegenModel = codegen.fromModel("Dog", openAPI.getComponents().getSchemas().get("Dog"));

        assertEquals(1, codegenModel.vars.size());
    }

    @Test
    public void arrayInnerReferencedSchemaMarkedAsModel_20() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/arrayRefBody.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Set<String> imports = new HashSet<>();

        RequestBody body = openAPI.getPaths().get("/examples").getPost().getRequestBody();

        CodegenParameter codegenParameter = codegen.fromRequestBody(body, imports, "");

        assertTrue(codegenParameter.isContainer);
        assertTrue(codegenParameter.items.isModel);
        assertFalse(codegenParameter.items.isContainer);
    }

    @Test
    public void arrayInnerReferencedSchemaMarkedAsModel_30() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/arrayRefBody.yaml");
        new InlineModelResolver().flatten(openAPI);
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Set<String> imports = new HashSet<>();

        RequestBody body = openAPI.getPaths().get("/examples").getPost().getRequestBody();

        CodegenParameter codegenParameter = codegen.fromRequestBody(body, imports, "");

        assertTrue(codegenParameter.isContainer);
        assertTrue(codegenParameter.items.isModel);
        assertFalse(codegenParameter.items.isContainer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void commonLambdasRegistrationTest() {

        DefaultCodegen codegen = new DefaultCodegen();
        Object lambdasObj = codegen.additionalProperties.get("lambda");

        assertNotNull(lambdasObj, "Expecting lambda in additionalProperties");

        Map<String, Lambda> lambdas = (Map<String, Lambda>) lambdasObj;

        assertTrue(lambdas.get("lowercase") instanceof LowercaseLambda, "Expecting LowercaseLambda class");
        assertTrue(lambdas.get("uppercase") instanceof UppercaseLambda, "Expecting UppercaseLambda class");
        assertTrue(lambdas.get("titlecase") instanceof TitlecaseLambda, "Expecting TitlecaseLambda class");
        assertTrue(lambdas.get("camelcase") instanceof CamelCaseAndSanitizeLambda, "Expecting CamelCaseAndSanitizeLambda class");
        assertTrue(lambdas.get("indented") instanceof IndentedLambda, "Expecting IndentedLambda class");
        assertTrue(lambdas.get("indented_8") instanceof IndentedLambda, "Expecting IndentedLambda class");
        assertTrue(lambdas.get("indented_12") instanceof IndentedLambda, "Expecting IndentedLambda class");
        assertTrue(lambdas.get("indented_16") instanceof IndentedLambda, "Expecting IndentedLambda class");
    }

    @Test
    public void convertApiNameWithEmptySuffix() {
        DefaultCodegen codegen = new DefaultCodegen();
        assertEquals("FakeApi", codegen.toApiName("Fake"));
        assertEquals("DefaultApi", codegen.toApiName(""));
    }

    @Test
    public void convertApiNameWithSuffix() {
        DefaultCodegen codegen = new DefaultCodegen();
        codegen.setApiNameSuffix("Test");
        assertEquals("FakeTest", codegen.toApiName("Fake"));
        assertEquals("DefaultApi", codegen.toApiName(""));
    }

    @Test
    public void testCircularReferencesDetection() {
        // given
        DefaultCodegen codegen = new DefaultCodegen();
        final CodegenProperty inboundOut = new CodegenProperty();
        inboundOut.baseName = "out";
        inboundOut.dataType = "RoundA";
        final CodegenProperty roundANext = new CodegenProperty();
        roundANext.baseName = "next";
        roundANext.dataType = "RoundB";
        final CodegenProperty roundBNext = new CodegenProperty();
        roundBNext.baseName = "next";
        roundBNext.dataType = "RoundC";
        final CodegenProperty roundCNext = new CodegenProperty();
        roundCNext.baseName = "next";
        roundCNext.dataType = "RoundA";
        final CodegenProperty roundCOut = new CodegenProperty();
        roundCOut.baseName = "out";
        roundCOut.dataType = "Outbound";
        final CodegenModel inboundModel = new CodegenModel();
        inboundModel.setDataType("Inbound");
        inboundModel.setAllVars(Collections.singletonList(inboundOut));
        final CodegenModel roundAModel = new CodegenModel();
        roundAModel.setDataType("RoundA");
        roundAModel.setAllVars(Collections.singletonList(roundANext));
        final CodegenModel roundBModel = new CodegenModel();
        roundBModel.setDataType("RoundB");
        roundBModel.setAllVars(Collections.singletonList(roundBNext));
        final CodegenModel roundCModel = new CodegenModel();
        roundCModel.setDataType("RoundC");
        roundCModel.setAllVars(Arrays.asList(roundCNext, roundCOut));
        final CodegenModel outboundModel = new CodegenModel();
        outboundModel.setDataType("Outbound");
        final Map<String, CodegenModel> models = new HashMap<>();
        models.put("Inbound", inboundModel);
        models.put("RoundA", roundAModel);
        models.put("RoundB", roundBModel);
        models.put("RoundC", roundCModel);
        models.put("Outbound", outboundModel);

        // when
        codegen.setCircularReferences(models);

        // then
        assertFalse(inboundOut.isCircularReference);
        assertTrue(roundANext.isCircularReference);
        assertTrue(roundBNext.isCircularReference);
        assertTrue(roundCNext.isCircularReference);
        assertFalse(roundCOut.isCircularReference);
    }

    @Test
    public void testUseOneOfInterfaces() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/composed-oneof.yaml");
        final DefaultCodegen cg = new DefaultCodegen();
        cg.setUseOneOfInterfaces(true);
        cg.preprocessOpenAPI(openAPI);

        // assert names of the response/request schema oneOf interfaces are as expected
        Schema s = ModelUtils.getReferencedSchema(openAPI, openAPI.getPaths()
                .get("/state")
                .getPost()
                .getRequestBody()
                .getContent()
                .get(APP_JSON)
                .getSchema());
        assertEquals("CreateStateRequest", s.getExtensions().get("x-one-of-name"));

        assertEquals(
                "#/components/schemas/getState_200_response",
                openAPI.getPaths()
                        .get("/state")
                        .getGet()
                        .getResponses()
                        .get("200")
                        .getContent()
                        .get(APP_JSON)
                        .getSchema().get$ref()
        );
        Schema getState200 = openAPI.getComponents().getSchemas().get("getState_200_response");
        //Assertions.assertEquals(getState200, "");
        assertEquals("GetState200Response", getState200.getExtensions().get("x-one-of-name"));

        // for the array schema, assert that a oneOf interface was added to schema map
        Schema items = ((ArraySchema) openAPI.getComponents().getSchemas().get("CustomOneOfArraySchema")).getItems();
        assertEquals("#/components/schemas/CustomOneOfArraySchema_inner", items.get$ref());
        //Assertions.assertEquals(items.get$ref(), "#/components/schemas/createState_request");
        Schema innerItem = ModelUtils.getReferencedSchema(openAPI, openAPI.getComponents().getSchemas().get("CustomOneOfArraySchema_inner"));
        assertEquals("CustomOneOfArraySchemaInner", innerItem.getExtensions().get("x-one-of-name"));
    }

    @Test
    public void testFormComposedSchema() {
        OpenAPI openAPI = TestUtils.parseContent("openapi: 3.0.1\n" +
                "info:\n" +
                "  version: '1.0.0'\n" +
                "  title: the title\n" +
                "\n" +
                "paths:\n" +
                "  '/users/me':\n" +
                "    post:\n" +
                "      description: Change user password.\n" +
                "      operationId: changeCurrentUserPassword\n" +
                "      requestBody:\n" +
                "        required: true\n" +
                "        content:\n" +
                "          multipart/form-data:\n" +
                "            schema:\n" +
                "              $ref: '#/components/schemas/ChangePasswordRequest'\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: Successful operation\n" +
                "          content: {}\n" +
                "\n" +
                "components:\n" +
                "  schemas:\n" +
                "    CommonPasswordRequest:\n" +
                "      type: object\n" +
                "      required: [ password, passwordConfirmation ]\n" +
                "      properties:\n" +
                "        password:\n" +
                "          type: string\n" +
                "          format: password\n" +
                "        passwordConfirmation:\n" +
                "          type: string\n" +
                "          format: password\n" +
                "\n" +
                "    ChangePasswordRequest:\n" +
                "      type: object\n" +
                "      allOf:\n" +
                "        - $ref: '#/components/schemas/CommonPasswordRequest'\n" +
                "        - type: object\n" +
                "          required: [ oldPassword ]\n" +
                "          properties:\n" +
                "            oldPassword:\n" +
                "              type: string\n" +
                "              format: password\n");

        final DefaultCodegen cg = new DefaultCodegen();
        cg.setOpenAPI(openAPI);
        cg.setUseOneOfInterfaces(true);
        cg.preprocessOpenAPI(openAPI);

        final PathItem path = openAPI.getPaths().get("/users/me");
        final CodegenOperation operation = cg.fromOperation(
                "/users/me",
                "post",
                path.getPost(),
                path.getServers());
        assertEquals(3, operation.formParams.size(),
                "The list of parameters should include inherited type");

        final List<String> names = operation.formParams.stream()
                .map(param -> param.paramName)
                .collect(Collectors.toList());
        assertTrue(names.contains("password"));
        assertTrue(names.contains("passwordConfirmation"));
        assertTrue(names.contains("oldPassword"));

        Optional<CodegenParameter> passwordParameter = operation.formParams.stream().filter(p -> "password".equals(p.paramName)).findFirst();
        assertTrue(passwordParameter.isPresent());
        assertTrue(passwordParameter.get().isPassword);
    }

    @Test
    public void inlineAllOfSchemaDoesNotThrowException() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue7262.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "UserTimeBase";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        final Set<CodegenDiscriminator.MappedModel> expectedMappedModels = Sets.newHashSet(new CodegenDiscriminator.MappedModel("UserSleep", "UserSleep"));
        final Set<CodegenDiscriminator.MappedModel> mappedModels = cm.getDiscriminator().getMappedModels();
        assertEquals(mappedModels, expectedMappedModels);

        modelName = "UserSleep";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        final Set<String> expectedAllOf = new HashSet<>(Arrays.asList("UserTimeBase"));
        assertEquals(cm.allOf, expectedAllOf);
        assertEquals(2, openAPI.getComponents().getSchemas().size());
        assertNull(cm.getDiscriminator());
    }

    @Test
    public void arrayModelHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue7356.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "ArrayWithValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);
        assertEquals(1, (int) cm.getMinItems());
    }

    @Test
    public void testFreeFormSchemas() throws Exception {
        File output = Files.createTempDirectory("test").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setInputSpec("src/test/resources/3_0/issue_7361.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(clientOptInput).generate();

        TestUtils.ensureDoesNotContainFile(files, output, "src/main/java/org/openapitools/client/model/FreeFormWithValidation.java");
        TestUtils.ensureDoesNotContainFile(files, output, "src/main/java/org/openapitools/client/model/FreeFormInterface.java");
        TestUtils.ensureDoesNotContainFile(files, output, "src/main/java/org/openapitools/client/model/FreeForm.java");
        output.deleteOnExit();
    }

    @Test
    public void testOauthMultipleFlows() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7193.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final Map<String, SecurityScheme> securitySchemes = openAPI.getComponents().getSecuritySchemes();
        final List<CodegenSecurity> securities = codegen.fromSecurity(securitySchemes);

        assertEquals(2, securities.size());
        final List<String> flows = securities.stream().map(c -> c.flow).collect(Collectors.toList());
        assertTrue(flows.containsAll(Arrays.asList("password", "application")));
    }

    @Test
    public void testMultipleSecuritySchemes() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/petstore.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final Map<String, SecurityScheme> securitySchemes = openAPI.getComponents().getSecuritySchemes();
        final List<CodegenSecurity> securities = codegen.fromSecurity(securitySchemes);

        assertEquals(2, securities.size());
        assertEquals("petstore_auth", securities.get(0).name);
        assertEquals("api_key", securities.get(1).name);
    }

    @Test
    public void testOpenIdConnectSecuritySchemes() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_17376.json");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        final Map<String, SecurityScheme> securitySchemes = openAPI.getComponents().getSecuritySchemes();
        final List<CodegenSecurity> securities = codegen.fromSecurity(securitySchemes);

        assertEquals(1, securities.size());
        assertEquals("Our Identity service", securities.get(0).name);
    }

    @Test
    public void testItemsPresent() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName;
        Schema sc;
        CodegenModel cm;

        modelName = "ArrayWithValidationsInItems";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertEquals("7", cm.getItems().getMaximum());

        modelName = "ObjectWithValidationsInArrayPropItems";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertEquals("7", cm.getVars().get(0).getItems().getMaximum());

        String path;
        Operation operation;
        CodegenOperation co;

        path = "/ref_array_with_validations_in_items/{items}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertEquals("7", co.pathParams.get(0).getItems().getMaximum());
        assertEquals("7", co.bodyParams.get(0).getItems().getMaximum());
        assertEquals("7", co.responses.get(0).getItems().getMaximum());

        path = "/array_with_validations_in_items/{items}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertEquals("7", co.pathParams.get(0).getItems().getMaximum());
        assertEquals("7", co.bodyParams.get(0).getItems().getMaximum());
        assertEquals("7", co.responses.get(0).getItems().getMaximum());
    }

    @Test
    public void testAdditionalPropertiesPresentInModels() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String modelName;
        Schema sc;
        CodegenModel cm;
        CodegenProperty anyTypeSchema = codegen.fromProperty("additional_properties", new Schema());

        modelName = "AdditionalPropertiesUnset";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertEquals(cm.getAdditionalProperties(), anyTypeSchema);
        assertTrue(cm.getAdditionalPropertiesIsAnyType());

        modelName = "AdditionalPropertiesTrue";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertEquals(cm.getAdditionalProperties(), anyTypeSchema);
        assertTrue(cm.getAdditionalPropertiesIsAnyType());

        modelName = "AdditionalPropertiesFalse";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertNull(cm.getAdditionalProperties());
        assertFalse(cm.getAdditionalPropertiesIsAnyType());

        modelName = "AdditionalPropertiesSchema";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        CodegenProperty stringCp = codegen.fromProperty("additional_properties", new Schema().type("string"));
        assertEquals(cm.getAdditionalProperties(), stringCp);
        assertTrue(cm.getAdditionalPropertiesIsAnyType());
    }

    @Test
    public void testAdditionalPropertiesPresentInModelProperties() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String modelName;
        Schema sc;
        CodegenModel cm;
        CodegenProperty anyTypeSchema = codegen.fromProperty("additional_properties", new Schema());
        CodegenProperty stringCp = codegen.fromProperty("additional_properties", new Schema().type("string"));
        CodegenProperty mapWithAddPropsUnset;
        CodegenProperty mapWithAddPropsTrue;
        CodegenProperty mapWithAddPropsFalse;
        CodegenProperty mapWithAddPropsSchema;

        // make sure isGenerateAliasAsModel is false
        boolean isGenerateAliasAsModel = ModelUtils.isGenerateAliasAsModel();
        if (isGenerateAliasAsModel) {
            GlobalSettings.setProperty("generateAliasAsModel", "false");
        }

        modelName = "ObjectModelWithRefAddPropsInProps";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        mapWithAddPropsUnset = cm.getVars().get(0);
        assertEquals(mapWithAddPropsUnset.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsUnset.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsTrue = cm.getVars().get(1);
        assertEquals(mapWithAddPropsTrue.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsTrue.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsFalse = cm.getVars().get(2);
        assertNull(mapWithAddPropsFalse.getAdditionalProperties());
        assertFalse(mapWithAddPropsFalse.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsSchema = cm.getVars().get(3);
        assertEquals(mapWithAddPropsSchema.getAdditionalProperties(), stringCp);
        assertTrue(mapWithAddPropsSchema.getAdditionalPropertiesIsAnyType());

        modelName = "ObjectModelWithAddPropsInProps";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        mapWithAddPropsUnset = cm.getVars().get(0);
        assertEquals(mapWithAddPropsUnset.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsUnset.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsTrue = cm.getVars().get(1);
        assertEquals(mapWithAddPropsTrue.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsTrue.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsFalse = cm.getVars().get(2);
        assertNull(mapWithAddPropsFalse.getAdditionalProperties());
        assertFalse(mapWithAddPropsFalse.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsSchema = cm.getVars().get(3);
        assertEquals(mapWithAddPropsSchema.getAdditionalProperties(), stringCp);
        assertTrue(mapWithAddPropsSchema.getAdditionalPropertiesIsAnyType());

        if (isGenerateAliasAsModel) { // restore the setting
            GlobalSettings.setProperty("generateAliasAsModel", "true");
        }
    }

    @Test
    public void testAdditionalPropertiesPresentInParameters() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path;
        Operation operation;
        CodegenOperation co;

        CodegenProperty anyTypeSchema = codegen.fromProperty("additional_properties", new Schema());
        CodegenProperty stringCp = codegen.fromProperty("additional_properties", new Schema().type("string"));
        CodegenParameter mapWithAddPropsUnset;
        CodegenParameter mapWithAddPropsTrue;
        CodegenParameter mapWithAddPropsFalse;
        CodegenParameter mapWithAddPropsSchema;

        // make sure isGenerateAliasAsModel is false
        boolean isGenerateAliasAsModel = ModelUtils.isGenerateAliasAsModel();
        if (isGenerateAliasAsModel) {
            GlobalSettings.setProperty("generateAliasAsModel", "false");
        }

        path = "/ref_additional_properties/";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        mapWithAddPropsUnset = co.queryParams.get(0);
        assertEquals(mapWithAddPropsUnset.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsUnset.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsTrue = co.queryParams.get(1);
        assertEquals(mapWithAddPropsTrue.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsTrue.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsFalse = co.queryParams.get(2);
        assertNull(mapWithAddPropsFalse.getAdditionalProperties());
        assertFalse(mapWithAddPropsFalse.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsSchema = co.queryParams.get(3);
        assertEquals(mapWithAddPropsSchema.getAdditionalProperties(), stringCp);
        assertTrue(mapWithAddPropsSchema.getAdditionalPropertiesIsAnyType());

        path = "/additional_properties/";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        mapWithAddPropsUnset = co.queryParams.get(0);
        assertEquals(mapWithAddPropsUnset.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsUnset.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsTrue = co.queryParams.get(1);
        assertEquals(mapWithAddPropsTrue.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsTrue.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsFalse = co.queryParams.get(2);
        assertNull(mapWithAddPropsFalse.getAdditionalProperties());
        assertFalse(mapWithAddPropsFalse.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsSchema = co.queryParams.get(3);
        assertEquals(mapWithAddPropsSchema.getAdditionalProperties(), stringCp);
        assertTrue(mapWithAddPropsSchema.getAdditionalPropertiesIsAnyType());

        if (isGenerateAliasAsModel) { // restore the setting
            GlobalSettings.setProperty("generateAliasAsModel", "true");
        }
    }

    @Test
    public void testAdditionalPropertiesPresentInResponses() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path;
        Operation operation;
        CodegenOperation co;

        CodegenProperty anyTypeSchema = codegen.fromProperty("additional_properties", new Schema());
        CodegenProperty stringCp = codegen.fromProperty("additional_properties", new Schema().type("string"));
        CodegenResponse mapWithAddPropsUnset;
        CodegenResponse mapWithAddPropsTrue;
        CodegenResponse mapWithAddPropsFalse;
        CodegenResponse mapWithAddPropsSchema;

        // make sure isGenerateAliasAsModel is false
        boolean isGenerateAliasAsModel = ModelUtils.isGenerateAliasAsModel();
        if (isGenerateAliasAsModel) {
            GlobalSettings.setProperty("generateAliasAsModel", "false");
        }

        path = "/ref_additional_properties/";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        mapWithAddPropsUnset = co.responses.get(0);
        assertEquals(mapWithAddPropsUnset.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsUnset.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsTrue = co.responses.get(1);
        assertEquals(mapWithAddPropsTrue.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsTrue.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsFalse = co.responses.get(2);
        assertNull(mapWithAddPropsFalse.getAdditionalProperties());
        assertFalse(mapWithAddPropsFalse.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsSchema = co.responses.get(3);
        assertEquals(mapWithAddPropsSchema.getAdditionalProperties(), stringCp);
        assertTrue(mapWithAddPropsSchema.getAdditionalPropertiesIsAnyType());

        path = "/additional_properties/";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        mapWithAddPropsUnset = co.responses.get(0);
        assertEquals(mapWithAddPropsUnset.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsUnset.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsTrue = co.responses.get(1);
        assertEquals(mapWithAddPropsTrue.getAdditionalProperties(), anyTypeSchema);
        assertTrue(mapWithAddPropsTrue.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsFalse = co.responses.get(2);
        assertNull(mapWithAddPropsFalse.getAdditionalProperties());
        assertFalse(mapWithAddPropsFalse.getAdditionalPropertiesIsAnyType());
        mapWithAddPropsSchema = co.responses.get(3);
        assertEquals(mapWithAddPropsSchema.getAdditionalProperties(), stringCp);
        assertTrue(mapWithAddPropsSchema.getAdditionalPropertiesIsAnyType());

        if (isGenerateAliasAsModel) { // restore the setting
            GlobalSettings.setProperty("generateAliasAsModel", "true");
        }
    }

    @Test
    public void testAdditionalPropertiesAnyType() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_9282.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        CodegenProperty anyTypeSchema = codegen.fromProperty("additional_properties", new Schema());

        Schema sc;
        CodegenModel cm;

        sc = openAPI.getComponents().getSchemas().get("AdditionalPropertiesTrue");
        cm = codegen.fromModel("AdditionalPropertiesTrue", sc);
        assertEquals(cm.getVars().get(0).additionalProperties, anyTypeSchema);

        sc = openAPI.getComponents().getSchemas().get("AdditionalPropertiesAnyType");
        cm = codegen.fromModel("AdditionalPropertiesAnyType", sc);
        assertEquals(cm.getVars().get(0).additionalProperties, anyTypeSchema);
    }

    @Test
    public void testIsXPresence() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName;
        Schema sc;
        CodegenModel cm;

        modelName = "DateWithValidation";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.isString);
        assertTrue(cm.isDate);

        modelName = "NullModel";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertTrue(cm.isNull);

        modelName = "ObjectWithTypeNullProperties";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertTrue(cm.getVars().get(0).isNull);
        assertTrue(cm.getVars().get(1).getItems().isNull);
        assertTrue(cm.getAdditionalProperties().isNull);

        modelName = "ArrayOfNulls";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertTrue(cm.getItems().isNull);

        modelName = "ObjectWithDateWithValidation";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.getVars().get(0).isString);
        assertTrue(cm.getVars().get(0).isDate);

        String path;
        Operation operation;
        CodegenOperation co;

        path = "/ref_date_with_validation/{date}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertFalse(co.pathParams.get(0).isString);
        assertTrue(co.pathParams.get(0).isDate);
        assertFalse(co.bodyParams.get(0).isString);
        assertTrue(co.bodyParams.get(0).isDate);
        assertFalse(co.responses.get(0).isString);
        assertTrue(co.responses.get(0).isDate);

        path = "/date_with_validation/{date}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertFalse(co.pathParams.get(0).isString);
        assertTrue(co.pathParams.get(0).isDate);
        assertFalse(co.bodyParams.get(0).isString);
        assertTrue(co.bodyParams.get(0).isDate);
        assertFalse(co.responses.get(0).isString);
        assertTrue(co.responses.get(0).isDate);

        modelName = "DateTimeWithValidation";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.isString);
        assertTrue(cm.isDateTime);

        modelName = "ObjectWithDateTimeWithValidation";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.getVars().get(0).isString);
        assertTrue(cm.getVars().get(0).isDateTime);

        path = "/ref_date_time_with_validation/{dateTime}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertFalse(co.pathParams.get(0).isString);
        assertTrue(co.pathParams.get(0).isDateTime);
        assertFalse(co.bodyParams.get(0).isString);
        assertTrue(co.bodyParams.get(0).isDateTime);
        assertFalse(co.responses.get(0).isString);
        assertTrue(co.responses.get(0).isDateTime);

        path = "/date_time_with_validation/{dateTime}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertFalse(co.pathParams.get(0).isString);
        assertTrue(co.pathParams.get(0).isDateTime);
        assertFalse(co.bodyParams.get(0).isString);
        assertTrue(co.bodyParams.get(0).isDateTime);
        assertFalse(co.responses.get(0).isString);
        assertTrue(co.responses.get(0).isDateTime);

        path = "/null/{param}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertTrue(co.pathParams.get(0).isNull);
        assertTrue(co.bodyParams.get(0).isNull);
        assertTrue(co.responses.get(0).isNull);

        path = "/ref_null/{param}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertTrue(co.pathParams.get(0).isNull);
        assertTrue(co.bodyParams.get(0).isNull);
        assertTrue(co.responses.get(0).isNull);
    }

    @Test
    public void testModelGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Schema sc;
        CodegenModel cm;

        List<String> modelNames = Arrays.asList(
                "ArrayWithMaxItems",
                "ArrayWithMinItems",
                "ArrayWithUniqueItems",
                "ObjectWithMinProperties",
                "ObjectWithMaxProperties",
                "StringWithMinLength",
                "DateWithMinLength",
                "DateTimeWithMinLength",
                "ByteWithMinLength",
                "BinaryWithMinLength",
                "StringWithMaxLength",
                "DateWithMaxLength",
                "DateTimeWithMaxLength",
                "ByteWithMaxLength",
                "BinaryWithMaxLength",
                "IntegerWithMultipleOf",
                "Integer32WithMultipleOf",
                "Integer64WithMultipleOf",
                "NumberWithMultipleOf",
                "NumberFloatWithMultipleOf",
                "NumberDoubleWithMultipleOf",
                "StringWithPattern",
                "DateWithPattern",
                "DateTimeWithPattern",
                "ByteWithPattern",
                "BinaryWithPattern",
                "IntegerWithMinimum",
                "Integer32WithMinimum",
                "Integer64WithMinimum",
                "NumberWithMinimum",
                "NumberFloatWithMinimum",
                "NumberDoubleWithMinimum",
                "IntegerWithMaximum",
                "Integer32WithMaximum",
                "Integer64WithMaximum",
                "NumberWithMaximum",
                "NumberFloatWithMaximum",
                "NumberDoubleWithMaximum",
                "IntegerWithExclusiveMaximum",
                "Integer32WithExclusiveMaximum",
                "Integer64WithExclusiveMaximum",
                "NumberWithExclusiveMaximum",
                "NumberFloatWithExclusiveMaximum",
                "NumberDoubleWithExclusiveMaximum",
                "IntegerWithExclusiveMinimum",
                "Integer32WithExclusiveMinimum",
                "Integer64WithExclusiveMinimum",
                "NumberWithExclusiveMinimum",
                "NumberFloatWithExclusiveMinimum",
                "NumberDoubleWithExclusiveMinimum"
        );
        for (String modelName : modelNames) {
            sc = openAPI.getComponents().getSchemas().get(modelName);
            cm = codegen.fromModel(modelName, sc);
            assertTrue(cm.getHasValidation());
        }
    }

    @Test
    public void testPropertyGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "ObjectWithPropertiesThatHaveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        List<CodegenProperty> props = cm.getVars();
        assertEquals(50, props.size());
        for (CodegenProperty prop : props) {
            assertTrue(prop.getHasValidation());
        }
    }

    @Test
    public void testQueryParametersGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String path = "/queryParametersWithValidation";
        Operation operation = openAPI.getPaths().get(path).getPost();
        CodegenOperation co = codegen.fromOperation(path, "POST", operation, null);
        List<CodegenParameter> params = co.queryParams;
        assertEquals(50, params.size());
        for (CodegenParameter param : params) {
            assertTrue(param.getHasValidation());
        }
    }

    @Test
    public void testHeaderParametersGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String path = "/headerParametersWithValidation";
        Operation operation = openAPI.getPaths().get(path).getPost();
        CodegenOperation co = codegen.fromOperation(path, "POST", operation, null);
        List<CodegenParameter> params = co.headerParams;
        assertEquals(50, params.size());
        for (CodegenParameter param : params) {
            assertTrue(param.getHasValidation());
        }
    }

    @Test
    public void testCookieParametersGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String path = "/cookieParametersWithValidation";
        Operation operation = openAPI.getPaths().get(path).getPost();
        CodegenOperation co = codegen.fromOperation(path, "POST", operation, null);
        List<CodegenParameter> params = co.cookieParams;
        assertEquals(50, params.size());
        for (CodegenParameter param : params) {
            assertTrue(param.getHasValidation());
        }
    }

    @Test
    public void testPathParametersGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String path = "/pathParametersWithValidation";
        Operation operation = openAPI.getPaths().get(path).getPost();
        CodegenOperation co = codegen.fromOperation(path, "POST", operation, null);
        List<CodegenParameter> params = co.pathParams;
        assertEquals(50, params.size());
        for (CodegenParameter param : params) {
            assertTrue(param.getHasValidation());
        }
    }

    @Test
    public void testBodyAndResponseGetHasValidation() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7651.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        List<String> modelNames = Arrays.asList(
                "ArrayWithMaxItems",
                "ArrayWithMinItems",
                "ArrayWithUniqueItems",
                "ObjectWithMinProperties",
                "ObjectWithMaxProperties",
                "StringWithMinLength",
                "DateWithMinLength",
                "DateTimeWithMinLength",
                "ByteWithMinLength",
                "BinaryWithMinLength",
                "StringWithMaxLength",
                "DateWithMaxLength",
                "DateTimeWithMaxLength",
                "ByteWithMaxLength",
                "BinaryWithMaxLength",
                "StringWithPattern",
                "DateWithPattern",
                "DateTimeWithPattern",
                "ByteWithPattern",
                "BinaryWithPattern",
                "IntegerWithMultipleOf",
                "Integer32WithMultipleOf",
                "Integer64WithMultipleOf",
                "NumberWithMultipleOf",
                "NumberFloatWithMultipleOf",
                "NumberDoubleWithMultipleOf",
                "IntegerWithMinimum",
                "Integer32WithMinimum",
                "Integer64WithMinimum",
                "NumberWithMinimum",
                "NumberFloatWithMinimum",
                "NumberDoubleWithMinimum",
                "IntegerWithMaximum",
                "Integer32WithMaximum",
                "Integer64WithMaximum",
                "NumberWithMaximum",
                "NumberFloatWithMaximum",
                "NumberDoubleWithMaximum",
                "IntegerWithExclusiveMaximum",
                "Integer32WithExclusiveMaximum",
                "Integer64WithExclusiveMaximum",
                "NumberWithExclusiveMaximum",
                "NumberFloatWithExclusiveMaximum",
                "NumberDoubleWithExclusiveMaximum",
                "IntegerWithExclusiveMinimum",
                "Integer32WithExclusiveMinimum",
                "Integer64WithExclusiveMinimum",
                "NumberWithExclusiveMinimum",
                "NumberFloatWithExclusiveMinimum",
                "NumberDoubleWithExclusiveMinimum"
        );

        String path;
        Operation operation;
        CodegenOperation co;

        for (String modelName : modelNames) {
            path = "/" + modelName;
            operation = openAPI.getPaths().get(path).getPost();
            co = codegen.fromOperation(path, "POST", operation, null);
            assertTrue(co.bodyParam.getHasValidation());
            assertTrue(co.responses.get(0).getHasValidation());
        }
    }

    @Test
    public void testVarsAndRequiredVarsPresent() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String modelName;
        Schema sc;
        CodegenModel cm;
        CodegenProperty propA = codegen.fromProperty("a", new Schema().type("string").minLength(1));
        propA.setRequired(true);
        CodegenProperty propB = codegen.fromProperty("b", new Schema().type("string").minLength(1));
        propB.setRequired(true);
        CodegenProperty propC = codegen.fromProperty("c", new Schema().type("string").minLength(1));
        propC.setRequired(false);

        List<CodegenProperty> vars = new ArrayList<>(Arrays.asList(propA, propB, propC));
        List<CodegenProperty> requiredVars = new ArrayList<>(Arrays.asList(propA, propB));

        modelName = "ObjectWithOptionalAndRequiredProps";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertEquals(cm.vars, vars);
        assertEquals(cm.requiredVars, requiredVars);

        String path;
        Operation operation;
        CodegenOperation co;

        path = "/object_with_optional_and_required_props/{objectData}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertEquals(co.pathParams.get(0).vars, vars);
        assertEquals(co.pathParams.get(0).requiredVars, requiredVars);
        assertEquals(co.bodyParams.get(0).vars, vars);
        assertEquals(co.bodyParams.get(0).requiredVars, requiredVars);

        // CodegenOperation puts the inline schema into schemas and refs it
        assertTrue(co.responses.get(0).isModel);
        assertEquals("objectWithOptionalAndRequiredProps_request", co.responses.get(0).baseType);
        modelName = "objectWithOptionalAndRequiredProps_request";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertEquals(cm.vars, vars);
        assertEquals(cm.requiredVars, requiredVars);

        // CodegenProperty puts the inline schema into schemas and refs it
        modelName = "ObjectPropContainsProps";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        CodegenProperty cp = cm.getVars().get(0);
        assertTrue(cp.isModel);
        assertEquals("objectWithOptionalAndRequiredProps_request", cp.complexType);
    }

    @Test
    public void testHasVarsInModel() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        Schema sc;
        CodegenModel cm;
        List<String> modelNames;

        modelNames = Arrays.asList(
                "ArrayWithValidationsInItems",
                "ObjectWithValidationsInAdditionalProperties",
                "AdditionalPropertiesUnset",
                "AdditionalPropertiesTrue",
                "AdditionalPropertiesFalse",
                "AdditionalPropertiesSchema"
        );
        for (String modelName : modelNames) {
            sc = openAPI.getComponents().getSchemas().get(modelName);
            cm = codegen.fromModel(modelName, sc);
            assertFalse(cm.getHasVars());
        }

        modelNames = Arrays.asList(
                "ObjectModelWithRefAddPropsInProps",
                "ObjectModelWithAddPropsInProps",
                "ObjectWithOptionalAndRequiredProps",
                "ObjectPropContainsProps"
        );
        for (String modelName : modelNames) {
            sc = openAPI.getComponents().getSchemas().get(modelName);
            cm = codegen.fromModel(modelName, sc);
            assertTrue(cm.getHasVars());
        }
    }

    @Test
    public void testHasVarsInProperty() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        Schema sc;
        CodegenModel cm;
        List<String> modelNames;

        modelNames = Arrays.asList(
                "ObjectWithValidationsInArrayPropItems",
                "ObjectModelWithRefAddPropsInProps",
                "ObjectModelWithAddPropsInProps",
                "ObjectWithOptionalAndRequiredProps"
        );
        for (String modelName : modelNames) {
            sc = openAPI.getComponents().getSchemas().get(modelName);
            cm = codegen.fromModel(modelName, sc);
            assertFalse(cm.vars.get(0).getHasVars());
        }

        String modelName;
        modelName = "ArrayWithObjectWithPropsInItems";
        ArraySchema as = (ArraySchema) openAPI.getComponents().getSchemas().get(modelName);
        assertEquals("#/components/schemas/ArrayWithObjectWithPropsInItems_inner", as.getItems().get$ref());
        sc = openAPI.getComponents().getSchemas().get("ArrayWithObjectWithPropsInItems_inner");
        cm = codegen.fromModel(modelName, sc);
        assertTrue(cm.getHasVars());

        modelName = "ObjectWithObjectWithPropsInAdditionalProperties";
        MapSchema ms = (MapSchema) openAPI.getComponents().getSchemas().get(modelName);
        assertEquals("#/components/schemas/ArrayWithObjectWithPropsInItems_inner", as.getItems().get$ref());
        sc = openAPI.getComponents().getSchemas().get("ArrayWithObjectWithPropsInItems_inner");
        cm = codegen.fromModel(modelName, sc);
        assertTrue(cm.getHasVars());
    }

    @Test
    public void testHasVarsInParameter() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path;
        Operation operation;
        CodegenOperation co;

        path = "/array_with_validations_in_items/{items}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertFalse(co.pathParams.get(0).getHasVars());
        assertFalse(co.bodyParam.getHasVars());

        path = "/object_with_optional_and_required_props/{objectData}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertTrue(co.pathParams.get(0).getHasVars());
        assertTrue(co.bodyParam.getHasVars());
    }

    @Test
    public void testHasVarsInResponse() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7613.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path;
        Operation operation;
        CodegenOperation co;

        path = "/additional_properties/";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        assertFalse(co.responses.get(0).getHasVars());

        path = "/object_with_optional_and_required_props/{objectData}";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        // does not have vars because the inline schema was extracted into a component ref
        assertFalse(co.responses.get(0).getHasVars());
    }

    @Test
    public void testHasRequiredInModel() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_8906.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        Schema sc;
        CodegenModel cm;

        List<String> modelNamesWithoutRequired = Arrays.asList(
                "EmptyObject",
                "ObjectWithOptionalB",
                "AnyTypeNoPropertiesNoRequired",
                "AnyTypeHasPropertiesNoRequired",
                "AnyTypeNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectNoPropertiesNoRequired",
                "ObjectHasPropertiesNoRequired",
                "ObjectNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedNoAllofPropsNoPropertiesNoRequired",
                "ComposedNoAllofPropsHasPropertiesNoRequired",
                "ComposedNoAllofPropsNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofOptPropNoPropertiesNoRequired",
                "ComposedHasAllofOptPropHasPropertiesNoRequired",
                "ComposedHasAllofOptPropNoPropertiesHasRequired"  // TODO: hasRequired should be true, fix this
        );
        for (String modelName : modelNamesWithoutRequired) {
            sc = openAPI.getComponents().getSchemas().get(modelName);
            cm = codegen.fromModel(modelName, sc);
            assertFalse(cm.getHasRequired());
        }

        List<String> modelNamesWithRequired = Arrays.asList(
                "AnyTypeHasPropertiesHasRequired",
                "ObjectHasPropertiesHasRequired",
                "ComposedNoAllofPropsHasPropertiesHasRequired",
                "ComposedHasAllofOptPropHasPropertiesHasRequired",
                "ComposedHasAllofReqPropNoPropertiesNoRequired",  // TODO: hasRequired should be false, fix this
                "ComposedHasAllofReqPropHasPropertiesNoRequired",  // TODO: hasRequired should be false, fix this
                "ComposedHasAllofReqPropNoPropertiesHasRequired",
                "ComposedHasAllofReqPropHasPropertiesHasRequired"
        );
        for (String modelName : modelNamesWithRequired) {
            sc = openAPI.getComponents().getSchemas().get(modelName);
            cm = codegen.fromModel(modelName, sc);
            assertTrue(cm.getHasRequired());
        }
    }

    @Test
    public void testHasRequiredInProperties() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_8906.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String modelName = "CodegenPropertiesModel";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        HashSet<String> modelNamesWithoutRequired = new HashSet(Arrays.asList(
                "EmptyObject",
                "ObjectWithOptionalB",
                "AnyTypeNoPropertiesNoRequired",
                "AnyTypeHasPropertiesNoRequired",
                "AnyTypeNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "AnyTypeHasPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectNoPropertiesNoRequired",
                "ObjectHasPropertiesNoRequired", // Note: this is extracted into another component and is a ref
                "ObjectNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedNoAllofPropsNoPropertiesNoRequired",
                "ComposedNoAllofPropsHasPropertiesNoRequired",
                "ComposedNoAllofPropsNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofOptPropNoPropertiesNoRequired",
                "ComposedHasAllofOptPropHasPropertiesNoRequired",
                "ComposedHasAllofOptPropNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectHasPropertiesHasRequired", // False because this is extracted into another component and is a ref
                "ComposedNoAllofPropsHasPropertiesHasRequired", // False because this is extracted into another component and is a ref
                "ComposedHasAllofOptPropHasPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofReqPropNoPropertiesNoRequired",
                "ComposedHasAllofReqPropHasPropertiesNoRequired",
                "ComposedHasAllofReqPropNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofReqPropHasPropertiesHasRequired"  // TODO: hasRequired should be true, fix this
        ));
        HashSet<String> modelNamesWithRequired = new HashSet(Arrays.asList(
        ));
        for (CodegenProperty var : cm.getVars()) {
            boolean hasRequired = var.getHasRequired();
            if (modelNamesWithoutRequired.contains(var.name)) {
                assertFalse(hasRequired);
            } else if (modelNamesWithRequired.contains(var.name)) {
                assertTrue(hasRequired);
            } else {
                // All variables must be in the above sets
                fail();
            }
        }
    }

    @Test
    public void testHasRequiredInParameters() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_8906.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path = "/schemasInQueryParamsAndResponses";
        Operation operation = openAPI.getPaths().get(path).getPost();
        CodegenOperation co = codegen.fromOperation(path, "POST", operation, null);

        HashSet<String> modelNamesWithoutRequired = new HashSet(Arrays.asList(
                "EmptyObject",
                "ObjectWithOptionalB",
                "AnyTypeNoPropertiesNoRequired",
                "AnyTypeHasPropertiesNoRequired",
                "AnyTypeNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "AnyTypeHasPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectNoPropertiesNoRequired",
                "ObjectHasPropertiesNoRequired", // Note: this is extracted into another component and is a ref
                "ObjectNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedNoAllofPropsNoPropertiesNoRequired",
                "ComposedNoAllofPropsHasPropertiesNoRequired",
                "ComposedNoAllofPropsNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofOptPropNoPropertiesNoRequired",
                "ComposedHasAllofOptPropHasPropertiesNoRequired",
                "ComposedHasAllofOptPropNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectHasPropertiesHasRequired", // False because this is extracted into another component and is a ref
                "ComposedNoAllofPropsHasPropertiesHasRequired", // False because this is extracted into another component and is a ref
                "ComposedHasAllofOptPropHasPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofReqPropNoPropertiesNoRequired",
                "ComposedHasAllofReqPropHasPropertiesNoRequired",
                "ComposedHasAllofReqPropNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofReqPropHasPropertiesHasRequired"  // TODO: hasRequired should be true, fix this
        ));
        HashSet<String> modelNamesWithRequired = new HashSet(Arrays.asList(
        ));
        for (CodegenParameter param : co.pathParams) {
            boolean hasRequired = param.getHasRequired();
            if (modelNamesWithoutRequired.contains(param.baseName)) {
                assertFalse(hasRequired);
            } else if (modelNamesWithRequired.contains(param.baseName)) {
                assertTrue(hasRequired);
            } else {
                // All variables must be in the above sets
                fail();
            }
        }
    }

    @Test
    public void testHasRequiredInResponses() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_8906.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path = "/schemasInQueryParamsAndResponses";
        Operation operation = openAPI.getPaths().get(path).getPost();
        CodegenOperation co = codegen.fromOperation(path, "POST", operation, null);

        HashSet<String> modelNamesWithoutRequired = new HashSet(Arrays.asList(
                "EmptyObject",
                "ObjectWithOptionalB",
                "AnyTypeNoPropertiesNoRequired",
                "AnyTypeHasPropertiesNoRequired",
                "AnyTypeNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "AnyTypeHasPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectNoPropertiesNoRequired",
                "ObjectHasPropertiesNoRequired", // Note: this is extracted into another component and is a ref
                "ObjectNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedNoAllofPropsNoPropertiesNoRequired",
                "ComposedNoAllofPropsHasPropertiesNoRequired",
                "ComposedNoAllofPropsNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofOptPropNoPropertiesNoRequired",
                "ComposedHasAllofOptPropHasPropertiesNoRequired",
                "ComposedHasAllofOptPropNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ObjectHasPropertiesHasRequired", // False because this is extracted into another component and is a ref
                "ComposedNoAllofPropsHasPropertiesHasRequired", // False because this is extracted into another component and is a ref
                "ComposedHasAllofOptPropHasPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofReqPropNoPropertiesNoRequired",
                "ComposedHasAllofReqPropHasPropertiesNoRequired",
                "ComposedHasAllofReqPropNoPropertiesHasRequired",  // TODO: hasRequired should be true, fix this
                "ComposedHasAllofReqPropHasPropertiesHasRequired"  // TODO: hasRequired should be true, fix this
        ));
        HashSet<String> modelNamesWithRequired = new HashSet(Arrays.asList(
        ));
        for (CodegenResponse cr : co.responses) {
            boolean hasRequired = cr.getHasRequired();
            if (modelNamesWithoutRequired.contains(cr.message)) {
                assertFalse(hasRequired);
            } else if (modelNamesWithRequired.contains(cr.message)) {
                assertTrue(hasRequired);
            } else {
                // All variables must be in the above sets
                fail();
            }
        }
    }

    @Test
    public void testBooleansSetForIntSchemas() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_9447.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String modelName;
        Schema sc;
        CodegenModel cm;

        modelName = "UnboundedInteger";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertTrue(cm.isUnboundedInteger);
        assertTrue(cm.isInteger);
        assertFalse(cm.isShort);
        assertFalse(cm.isLong);

        modelName = "Int32";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.isUnboundedInteger);
        assertTrue(cm.isInteger);
        assertTrue(cm.isShort);
        assertFalse(cm.isLong);

        modelName = "Int64";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.isUnboundedInteger);
        assertFalse(cm.isInteger);
        assertFalse(cm.isShort);
        assertTrue(cm.isLong);

        modelName = "ObjectModelWithIntegerProps";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        cm = codegen.fromModel(modelName, sc);
        assertFalse(cm.isUnboundedInteger);
        assertFalse(cm.isInteger);
        assertFalse(cm.isShort);
        assertFalse(cm.isLong);
        CodegenProperty cp;
        cp = cm.vars.get(0);
        assertTrue(cp.isUnboundedInteger);
        assertTrue(cp.isInteger);
        assertFalse(cp.isShort);
        assertFalse(cp.isLong);
        cp = cm.vars.get(1);
        assertFalse(cp.isUnboundedInteger);
        assertTrue(cp.isInteger);
        assertTrue(cp.isShort);
        assertFalse(cp.isLong);
        cp = cm.vars.get(2);
        assertFalse(cp.isUnboundedInteger);
        assertFalse(cp.isInteger);
        assertFalse(cp.isShort);
        assertTrue(cp.isLong);

        String path;
        Operation operation;
        CodegenOperation co;
        CodegenParameter cpa;
        CodegenResponse cr;

        path = "/UnboundedInteger";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        cpa = co.pathParams.get(0);
        assertTrue(cpa.isUnboundedInteger);
        assertTrue(cpa.isInteger);
        assertFalse(cpa.isShort);
        assertFalse(cpa.isLong);
        cpa = co.bodyParam;
        assertTrue(cpa.isUnboundedInteger);
        assertTrue(cpa.isInteger);
        assertFalse(cpa.isShort);
        assertFalse(cpa.isLong);
        cr = co.responses.get(0);
        assertTrue(cr.isUnboundedInteger);
        assertTrue(cr.isInteger);
        assertFalse(cr.isShort);
        assertFalse(cr.isLong);

        path = "/Int32";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        cpa = co.pathParams.get(0);
        assertFalse(cpa.isUnboundedInteger);
        assertTrue(cpa.isInteger);
        assertTrue(cpa.isShort);
        assertFalse(cpa.isLong);
        cpa = co.bodyParam;
        assertFalse(cpa.isUnboundedInteger);
        assertTrue(cpa.isInteger);
        assertTrue(cpa.isShort);
        assertFalse(cpa.isLong);
        cr = co.responses.get(0);
        assertFalse(cr.isUnboundedInteger);
        assertTrue(cr.isInteger);
        assertTrue(cr.isShort);
        assertFalse(cr.isLong);

        path = "/Int64";
        operation = openAPI.getPaths().get(path).getPost();
        co = codegen.fromOperation(path, "POST", operation, null);
        cpa = co.pathParams.get(0);
        assertFalse(cpa.isUnboundedInteger);
        assertFalse(cpa.isInteger);
        assertFalse(cpa.isShort);
        assertTrue(cpa.isLong);
        cpa = co.bodyParam;
        assertFalse(cpa.isUnboundedInteger);
        assertFalse(cpa.isInteger);
        assertFalse(cpa.isShort);
        assertTrue(cpa.isLong);
        cr = co.responses.get(0);
        assertFalse(cr.isUnboundedInteger);
        assertFalse(cr.isInteger);
        assertFalse(cr.isShort);
        assertTrue(cr.isLong);
    }

    @Test
    public void testRemoveOperationIdPrefix() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/bugs/issue_9719.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path;
        Operation operation;
        CodegenOperation co;

        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX, "True");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_DELIMITER, ".");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_COUNT, 2);
        codegen.processOpts();
        path = "/dotDelimiter";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertEquals("usersGetAll", co.operationId);

        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX, "True");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_DELIMITER, ".");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_COUNT, -1);
        codegen.processOpts();
        path = "/dotDelimiter";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertEquals("getAll", co.operationId);

        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX, "True");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_DELIMITER, ".");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_COUNT, 10);
        codegen.processOpts();
        path = "/dotDelimiter";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertEquals("getAll", co.operationId);

        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX, "True");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_DELIMITER, "_");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_COUNT, 2);
        codegen.processOpts();
        path = "/underscoreDelimiter";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertEquals("usersGetAll", co.operationId);

        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX, "True");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_DELIMITER, "_");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_COUNT, -1);
        codegen.processOpts();
        path = "/underscoreDelimiter";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertEquals("getAll", co.operationId);

        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX, "True");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_DELIMITER, "_");
        codegen.additionalProperties().put(CodegenConstants.REMOVE_OPERATION_ID_PREFIX_COUNT, 10);
        codegen.processOpts();
        path = "/underscoreDelimiter";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertEquals("getAll", co.operationId);
    }

    @Test
    @Ignore
    public void testComposedPropertyTypes() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_10330.yaml");
        codegen.setOpenAPI(openAPI);
        String modelName;

        modelName = "ObjectWithComposedProperties";
        CodegenModel m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        /* TODO inline allOf schema are created as separate models and the following assumptions that
           the properties are non-model are no longer valid and need to be revised
        assertTrue(m.vars.get(0).getIsMap());
        assertTrue(m.vars.get(1).getIsNumber());
        assertTrue(m.vars.get(2).getIsUnboundedInteger());
        assertTrue(m.vars.get(3).getIsString());
        assertTrue(m.vars.get(4).getIsBoolean());
        assertTrue(m.vars.get(5).getIsArray());
        assertTrue(m.vars.get(6).getIsNull());
        assertTrue(m.vars.get(7).getIsAnyType());
        */
    }

    @Test
    public void testComposedModelTypes() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_10330.yaml");
        codegen.setOpenAPI(openAPI);
        String modelName;
        CodegenModel m;

        modelName = "ComposedObject";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsMap());

        modelName = "ComposedNumber";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsNumber());

        modelName = "ComposedInteger";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsUnboundedInteger());

        modelName = "ComposedString";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsString());

        modelName = "ComposedBool";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsBoolean());

        modelName = "ComposedArray";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsArray());

        modelName = "ComposedNone";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsNull());

        modelName = "ComposedAnyType";
        m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        assertTrue(m.getIsAnyType());
    }

    @Test
    public void testComposedResponseTypes() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_10330.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;
        CodegenResponse cr;

        path = "/ComposedObject";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsMap());

        path = "/ComposedNumber";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsNumber());

        path = "/ComposedInteger";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsUnboundedInteger());

        path = "/ComposedString";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsString());

        path = "/ComposedBool";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsBoolean());

        path = "/ComposedArray";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsArray());

        path = "/ComposedNone";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsNull());

        path = "/ComposedAnyType";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cr = co.responses.get(0);
        assertTrue(cr.getIsAnyType());
    }

    @Test
    public void testComposedRequestBodyTypes() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_10330.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;
        CodegenParameter cp;

        path = "/ComposedNumber";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsNumber());

        path = "/ComposedInteger";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsUnboundedInteger());

        path = "/ComposedString";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsString());

        path = "/ComposedBool";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsBoolean());

        path = "/ComposedArray";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsArray());

        path = "/ComposedNone";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsNull());

        path = "/ComposedAnyType";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.bodyParam;
        assertTrue(cp.getIsAnyType());
    }

    @Test
    public void testComposedRequestQueryParamTypes() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_10330.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;
        CodegenParameter cp;

        path = "/ComposedObject";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsMap());

        path = "/ComposedNumber";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsNumber());

        path = "/ComposedInteger";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsUnboundedInteger());

        path = "/ComposedString";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsString());

        path = "/ComposedBool";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsBoolean());

        path = "/ComposedArray";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsArray());

        path = "/ComposedNone";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsNull());

        path = "/ComposedAnyType";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        cp = co.queryParams.get(0);
        assertTrue(cp.getIsAnyType());
    }

    @Test
    public void testByteArrayTypeInSchemas() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_10725.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;
        CodegenParameter cp;

        path = "/TxRxByteArray";
        co = codegen.fromOperation(path, "POST", openAPI.getPaths().get(path).getPost(), null);
        cp = co.bodyParam;
        assertTrue(cp.isByteArray);
        assertFalse(cp.getIsString());
        CodegenResponse cr = co.responses.get(0);
        assertTrue(cr.isByteArray);
        assertFalse(cr.getIsString());

        String modelName = "ObjectContainingByteArray";
        CodegenModel m = codegen.fromModel(modelName, openAPI.getComponents().getSchemas().get(modelName));
        CodegenProperty pr = m.vars.get(0);
        assertTrue(pr.isByteArray);
        assertFalse(pr.getIsString());
    }

    @Test
    public void testResponses() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/response-tests.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String path;
        Operation operation;
        CodegenOperation co;
        CodegenParameter cpa;
        CodegenResponse cr;

        path = "/pet/{petId}";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        //assertTrue(co.hasErrorResponseObject);
        cr = co.responses.get(0);
        assertTrue(cr.is2xx);
        assertFalse(cr.simpleType);
        assertFalse(cr.primitiveType);
        cr = co.responses.get(3);
        assertTrue(cr.is5xx);
        assertFalse(cr.simpleType);
        assertFalse(cr.primitiveType);

        path = "/pet";
        operation = openAPI.getPaths().get(path).getPut();
        co = codegen.fromOperation(path, "PUT", operation, null);
        assertTrue(co.hasErrorResponseObject);

        // 200 response
        cr = co.responses.get(0);
        assertTrue(cr.is2xx);
        assertFalse(cr.simpleType);
        assertFalse(cr.primitiveType);

        // 400 response
        cr = co.responses.get(1);
        assertTrue(cr.is4xx);
        assertEquals("400", cr.code);
        assertFalse(cr.simpleType);
        assertFalse(cr.primitiveType);

        path = "/pet/findByTags";
        operation = openAPI.getPaths().get(path).getGet();
        co = codegen.fromOperation(path, "GET", operation, null);
        assertFalse(co.hasErrorResponseObject);
        cr = co.responses.get(0);
        assertTrue(cr.is2xx);
        assertFalse(cr.simpleType);
        assertFalse(cr.primitiveType);
    }

    @Test
    public void testRequestParameterContent() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/content-data.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;

        path = "/jsonQueryParams";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        CodegenParameter coordinatesInlineSchema = co.queryParams.get(0);
        LinkedHashMap<String, CodegenMediaType> content = coordinatesInlineSchema.getContent();
        assertNotNull(content);
        assertEquals(content.keySet(), new HashSet<>(Arrays.asList(APP_JSON)));
        CodegenMediaType mt = content.get(APP_JSON);
        assertNull(mt.getEncoding());
        CodegenProperty cp = mt.getSchema();
        assertFalse(cp.isMap);
        assertTrue(cp.isModel);
        assertEquals("object", cp.complexType);
        assertEquals("SchemaForRequestParameterCoordinatesInlineSchemaApplicationJson", cp.baseName);

        CodegenParameter coordinatesReferencedSchema = co.queryParams.get(1);
        content = coordinatesReferencedSchema.getContent();
        mt = content.get(APP_JSON);
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertFalse(cp.isMap); // because it is a referenced schema
        assertEquals("coordinates", cp.complexType);
        assertEquals("SchemaForRequestParameterCoordinatesReferencedSchemaApplicationJson", cp.baseName);
    }

    @Test
    public void testRequestBodyContent() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/content-data.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;

        path = "/inlineRequestBodySchemasDifferingByContentType";
        co = codegen.fromOperation(path, "POST", openAPI.getPaths().get(path).getPost(), null);
        CodegenParameter bodyParameter = co.bodyParam;
        LinkedHashMap<String, CodegenMediaType> content = bodyParameter.getContent();
        assertNotNull(content);
        assertEquals(content.keySet(), new HashSet<>(Arrays.asList(APP_JSON, "text/plain")));
        CodegenMediaType mt = content.get(APP_JSON);
        assertNull(mt.getEncoding());
        CodegenProperty cp = mt.getSchema();
        assertEquals("SchemaForRequestBodyApplicationJson", cp.baseName);
        assertNotNull(cp);

        mt = content.get("text/plain");
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertEquals("SchemaForRequestBodyTextPlain", cp.baseName);
        assertNotNull(cp);
        // Note: the inline model resolver has a bug for this use case; it extracts an inline request body into a component
        // but the schema it references is not string type

        path = "/refRequestBodySchemasDifferingByContentType";
        co = codegen.fromOperation(path, "POST", openAPI.getPaths().get(path).getPost(), null);
        bodyParameter = co.bodyParam;
        content = bodyParameter.getContent();
        assertNotNull(content);
        assertEquals(content.keySet(), new HashSet<>(Arrays.asList(APP_JSON, "text/plain")));
        mt = content.get(APP_JSON);
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertEquals("SchemaForRequestBodyApplicationJson", cp.baseName);
        assertEquals("coordinates", cp.complexType);

        mt = content.get("text/plain");
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertEquals("SchemaForRequestBodyTextPlain", cp.baseName);
        assertTrue(cp.isString);

        path = "/requestBodyWithEncodingTypes";
        co = codegen.fromOperation(path, "POST", openAPI.getPaths().get(path).getPost(), null);
        List<CodegenParameter> formParams = co.formParams;

        assertEquals("intParam", formParams.get(0).paramName);
        assertEquals("intParam", formParams.get(0).nameInCamelCase);
        assertEquals("IntParam", formParams.get(0).nameInPascalCase);
        assertEquals("INT_PARAM", formParams.get(0).nameInSnakeCase);

        assertFalse(formParams.get(0).isContainer);
        assertFalse(formParams.get(0).isExplode); // Should not be true for non-container

        assertEquals("explodeTrue", formParams.get(1).paramName);
        assertEquals("explodeTrue", formParams.get(1).nameInCamelCase);
        assertEquals("ExplodeTrue", formParams.get(1).nameInPascalCase);
        assertEquals("EXPLODE_TRUE", formParams.get(1).nameInSnakeCase);
        assertEquals("explodetrue", formParams.get(1).nameInLowerCase);
        assertTrue(formParams.get(1).isContainer);
        assertEquals(formParams.get(1).style, Encoding.StyleEnum.FORM.toString());
        assertTrue(formParams.get(1).isExplode);
        assertNull(formParams.get(1).contentType);

        assertEquals("explodeFalse", formParams.get(2).paramName);
        assertEquals("explodeFalse", formParams.get(2).nameInCamelCase);
        assertEquals("ExplodeFalse", formParams.get(2).nameInPascalCase);
        assertEquals("EXPLODE_FALSE", formParams.get(2).nameInSnakeCase);
        assertEquals("explodefalse", formParams.get(2).nameInLowerCase);
        assertTrue(formParams.get(2).isContainer);
        assertEquals(formParams.get(2).style, Encoding.StyleEnum.FORM.toString());
        assertFalse(formParams.get(2).isExplode);
        assertNull(formParams.get(2).contentType);

        assertEquals("noStyleNoExplode", formParams.get(3).paramName);
        assertEquals("noStyleNoExplode", formParams.get(3).nameInCamelCase);
        assertEquals("NoStyleNoExplode", formParams.get(3).nameInPascalCase);
        assertEquals("NO_STYLE_NO_EXPLODE", formParams.get(3).nameInSnakeCase);
        assertEquals("nostylenoexplode", formParams.get(3).nameInLowerCase);
        assertTrue(formParams.get(3).isContainer);
        assertEquals(formParams.get(3).style, Encoding.StyleEnum.FORM.toString());
        assertTrue(formParams.get(3).isExplode); // Defaults to true for style == FORM
        assertEquals("text/plain", formParams.get(3).contentType);

        assertEquals("styleSpecified", formParams.get(4).paramName);
        assertEquals("styleSpecified", formParams.get(4).nameInCamelCase);
        assertEquals("StyleSpecified", formParams.get(4).nameInPascalCase);
        assertEquals("STYLE_SPECIFIED", formParams.get(4).nameInSnakeCase);
        assertEquals("stylespecified", formParams.get(4).nameInLowerCase);
        assertTrue(formParams.get(4).isContainer);
        assertEquals(formParams.get(4).style, Encoding.StyleEnum.SPACE_DELIMITED.toString());
        assertFalse(formParams.get(4).isExplode);
        assertNull(formParams.get(4).contentType);

        assertEquals("styleSpecifiedNoExplode", formParams.get(5).paramName);
        assertEquals("styleSpecifiedNoExplode", formParams.get(5).nameInCamelCase);
        assertEquals("StyleSpecifiedNoExplode", formParams.get(5).nameInPascalCase);
        assertEquals("STYLE_SPECIFIED_NO_EXPLODE", formParams.get(5).nameInSnakeCase);
        assertEquals("stylespecifiednoexplode", formParams.get(5).nameInLowerCase);
        assertTrue(formParams.get(5).isContainer);
        assertEquals(formParams.get(5).style, Encoding.StyleEnum.SPACE_DELIMITED.toString());
        assertFalse(formParams.get(5).isExplode); // Defaults to false for style other than FORM
        assertNull(formParams.get(5).contentType);
    }

    @Test
    public void testResponseContentAndHeader() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/content-data.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;

        path = "/jsonQueryParams";
        co = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);
        CodegenParameter coordinatesInlineSchema = co.queryParams.get(0);
        LinkedHashMap<String, CodegenMediaType> content = coordinatesInlineSchema.getContent();
        assertNotNull(content);
        assertEquals(content.keySet(), new HashSet<>(Arrays.asList(APP_JSON)));

        CodegenParameter schemaParam = co.queryParams.get(2);
        assertEquals("stringWithMinLength", schemaParam.getSchema().baseName);


        CodegenResponse cr = co.responses.get(0);
        List<CodegenParameter> responseHeaders = cr.getResponseHeaders();
        assertEquals(2, responseHeaders.size());
        CodegenParameter header1 = responseHeaders.get(0);
        assertEquals("X-Rate-Limit", header1.baseName);
        assertTrue(header1.isUnboundedInteger);
        assertEquals("X-Rate-Limit", header1.getSchema().baseName);

        CodegenParameter header2 = responseHeaders.get(1);
        assertEquals("X-Rate-Limit-Ref", header2.baseName);
        assertTrue(header2.isUnboundedInteger);
        assertEquals("X-Rate-Limit-Ref", header2.getSchema().baseName);

        content = cr.getContent();
        assertEquals(content.keySet(), new HashSet<>(Arrays.asList(APP_JSON, "text/plain")));
        CodegenMediaType mt = content.get(APP_JSON);
        assertNull(mt.getEncoding());
        CodegenProperty cp = mt.getSchema();
        assertFalse(cp.isMap); // because it is a referenced schema
        assertEquals("coordinates", cp.complexType);
        assertEquals("SchemaFor200ResponseBodyApplicationJson", cp.baseName);

        mt = content.get("text/plain");
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertEquals("SchemaFor200ResponseBodyTextPlain", cp.baseName);
        assertTrue(cp.isString);

        cr = co.responses.get(1);
        content = cr.getContent();
        assertEquals(content.keySet(), new HashSet<>(Arrays.asList(APP_JSON, "text/plain")));
        mt = content.get(APP_JSON);
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertFalse(cp.isMap); // because it is a referenced schema
        assertEquals("coordinates", cp.complexType);
        assertEquals("SchemaFor201ResponseBodyApplicationJson", cp.baseName);

        assertNotNull(mt.getExamples());
        assertEquals(2, mt.getExamples().size());

        mt = content.get("text/plain");
        assertNull(mt.getEncoding());
        cp = mt.getSchema();
        assertEquals("SchemaFor201ResponseBodyTextPlain", cp.baseName);
        assertTrue(cp.isString);
    }

    @Test
    public void testUnalias() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/schema-unalias-test.yml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Schema requestBodySchema = ModelUtils.getSchemaFromRequestBody(
                openAPI.getPaths().get("/thingy/{date}").getPost().getRequestBody());
        assertEquals("#/components/schemas/updatePetWithForm_request", requestBodySchema.get$ref());
        assertEquals("updatePetWithForm_request", ModelUtils.getSimpleRef(requestBodySchema.get$ref()));
        Assertions.assertNotNull(openAPI.getComponents().getSchemas().get(ModelUtils.getSimpleRef(requestBodySchema.get$ref())));

        Schema requestBodySchema2 = ModelUtils.unaliasSchema(openAPI, requestBodySchema);
        // get$ref is not null as unaliasSchema returns the schema with the last $ref to the actual schema
        Assertions.assertNotNull(requestBodySchema2.get$ref());
        assertEquals("#/components/schemas/updatePetWithForm_request", requestBodySchema2.get$ref());

        Schema requestBodySchema3 = ModelUtils.getReferencedSchema(openAPI, requestBodySchema);
        CodegenParameter codegenParameter = codegen.fromFormProperty("visitDate",
                (Schema) requestBodySchema3.getProperties().get("visitDate"), new HashSet<>());

        assertEquals("1971-12-19T03:39:57-08:00", codegenParameter.defaultValue);
        assertNull(codegenParameter.getSchema());
    }

    @Test
    public void testArraySchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "ArrayWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());


        // JUnit assertions
        assertEquals(16, logsList.size());
        assertEquals("Validation 'minProperties' has no effect on schema 'array'. Ignoring!", logsList.get(0)
                .getMessage());
        assertEquals("Validation 'maxProperties' has no effect on schema 'array'. Ignoring!", logsList.get(1)
                .getMessage());
        assertEquals("Validation 'minLength' has no effect on schema 'array'. Ignoring!", logsList.get(2)
                .getMessage());
        assertEquals("Validation 'maxLength' has no effect on schema 'array'. Ignoring!", logsList.get(3)
                .getMessage());
        assertEquals("Validation 'pattern' has no effect on schema 'array'. Ignoring!", logsList.get(4)
                .getMessage());
        assertEquals("Validation 'multipleOf' has no effect on schema 'array'. Ignoring!", logsList.get(5)
                .getMessage());
        assertEquals("Validation 'minimum' has no effect on schema 'array'. Ignoring!", logsList.get(6)
                .getMessage());
        assertEquals("Validation 'maximum' has no effect on schema 'array'. Ignoring!", logsList.get(7)
                .getMessage());

        // Assert all logged messages are WARN messages
        logsList.stream().limit(8).forEach(log -> assertEquals(Level.WARN, log.getLevel()));
    }

    @Test
    public void testObjectSchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "ObjectWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());

        // JUnit assertions
        assertEquals(9, logsList.size(), "Messages: " + logsList.stream().map(ILoggingEvent::getMessage).collect(Collectors.toList()));
        assertEquals("Validation 'minItems' has no effect on schema 'object'. Ignoring!", logsList.get(0)
                .getMessage());
        assertEquals("Validation 'maxItems' has no effect on schema 'object'. Ignoring!", logsList.get(1)
                .getMessage());
        assertEquals("Validation 'uniqueItems' has no effect on schema 'object'. Ignoring!", logsList.get(2)
                .getMessage());
        assertEquals("Validation 'minLength' has no effect on schema 'object'. Ignoring!", logsList.get(3)
                .getMessage());
        assertEquals("Validation 'maxLength' has no effect on schema 'object'. Ignoring!", logsList.get(4)
                .getMessage());
        assertEquals("Validation 'pattern' has no effect on schema 'object'. Ignoring!", logsList.get(5)
                .getMessage());
        assertEquals("Validation 'multipleOf' has no effect on schema 'object'. Ignoring!", logsList.get(6)
                .getMessage());
        assertEquals("Validation 'minimum' has no effect on schema 'object'. Ignoring!", logsList.get(7)
                .getMessage());
        assertEquals("Validation 'maximum' has no effect on schema 'object'. Ignoring!", logsList.get(8)
                .getMessage());

        // Assert all logged messages are WARN messages
        logsList.stream().forEach(log -> assertEquals(Level.WARN, log.getLevel()));
    }

    @Test
    public void testStringSchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "StringWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());

        // JUnit assertions
        assertEquals(8, logsList.size(), "Messages: " + logsList.stream().map(ILoggingEvent::getMessage).collect(Collectors.toList()));
        assertEquals("Validation 'minItems' has no effect on schema 'string'. Ignoring!", logsList.get(0)
                .getMessage());
        assertEquals("Validation 'maxItems' has no effect on schema 'string'. Ignoring!", logsList.get(1)
                .getMessage());
        assertEquals("Validation 'uniqueItems' has no effect on schema 'string'. Ignoring!", logsList.get(2)
                .getMessage());
        assertEquals("Validation 'minProperties' has no effect on schema 'string'. Ignoring!", logsList.get(3)
                .getMessage());
        assertEquals("Validation 'maxProperties' has no effect on schema 'string'. Ignoring!", logsList.get(4)
                .getMessage());
        assertEquals("Validation 'multipleOf' has no effect on schema 'string'. Ignoring!", logsList.get(5)
                .getMessage());
        assertEquals("Validation 'minimum' has no effect on schema 'string'. Ignoring!", logsList.get(6)
                .getMessage());
        assertEquals("Validation 'maximum' has no effect on schema 'string'. Ignoring!", logsList.get(7)
                .getMessage());

        // Assert all logged messages are WARN messages
        logsList.stream().forEach(log -> assertEquals(Level.WARN, log.getLevel()));
    }

    @Test
    public void testIntegerSchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "IntegerWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());

        // JUnit assertions
        assertEquals(8, logsList.size(), "Messages: " + logsList.stream().map(ILoggingEvent::getMessage).collect(Collectors.toList()));
        assertEquals("Validation 'minItems' has no effect on schema 'integer'. Ignoring!", logsList.get(0)
                .getMessage());
        assertEquals("Validation 'maxItems' has no effect on schema 'integer'. Ignoring!", logsList.get(1)
                .getMessage());
        assertEquals("Validation 'uniqueItems' has no effect on schema 'integer'. Ignoring!", logsList.get(2)
                .getMessage());
        assertEquals("Validation 'minProperties' has no effect on schema 'integer'. Ignoring!", logsList.get(3)
                .getMessage());
        assertEquals("Validation 'maxProperties' has no effect on schema 'integer'. Ignoring!", logsList.get(4)
                .getMessage());
        assertEquals("Validation 'minLength' has no effect on schema 'integer'. Ignoring!", logsList.get(5)
                .getMessage());
        assertEquals("Validation 'maxLength' has no effect on schema 'integer'. Ignoring!", logsList.get(6)
                .getMessage());
        assertEquals("Validation 'pattern' has no effect on schema 'integer'. Ignoring!", logsList.get(7)
                .getMessage());

        // Assert all logged messages are WARN messages
        logsList.stream().forEach(log -> assertEquals(Level.WARN, log.getLevel()));
    }

    @Test
    public void testAnySchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "AnyTypeWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());

        // JUnit assertions
        assertEquals(0, logsList.size(), "Messages: " + logsList.stream().map(ILoggingEvent::getMessage).collect(Collectors.toList()));
    }

    @Test
    public void testBooleanSchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "BooleanWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());

        // JUnit assertions
        assertEquals(11, logsList.size(), "Messages: " + logsList.stream().map(ILoggingEvent::getMessage).collect(Collectors.toList()));
        assertEquals("Validation 'minItems' has no effect on schema 'boolean'. Ignoring!", logsList.get(0)
                .getMessage());
        assertEquals("Validation 'maxItems' has no effect on schema 'boolean'. Ignoring!", logsList.get(1)
                .getMessage());
        assertEquals("Validation 'uniqueItems' has no effect on schema 'boolean'. Ignoring!", logsList.get(2)
                .getMessage());
        assertEquals("Validation 'minProperties' has no effect on schema 'boolean'. Ignoring!", logsList.get(3)
                .getMessage());
        assertEquals("Validation 'maxProperties' has no effect on schema 'boolean'. Ignoring!", logsList.get(4)
                .getMessage());
        assertEquals("Validation 'minLength' has no effect on schema 'boolean'. Ignoring!", logsList.get(5)
                .getMessage());
        assertEquals("Validation 'maxLength' has no effect on schema 'boolean'. Ignoring!", logsList.get(6)
                .getMessage());
        assertEquals("Validation 'pattern' has no effect on schema 'boolean'. Ignoring!", logsList.get(7)
                .getMessage());
        assertEquals("Validation 'multipleOf' has no effect on schema 'boolean'. Ignoring!", logsList.get(8)
                .getMessage());
        assertEquals("Validation 'minimum' has no effect on schema 'boolean'. Ignoring!", logsList.get(9)
                .getMessage());
        assertEquals("Validation 'maximum' has no effect on schema 'boolean'. Ignoring!", logsList.get(10)
                .getMessage());

        // Assert all logged messages are WARN messages
        logsList.stream().forEach(log -> assertEquals(Level.WARN, log.getLevel()));
    }

    @Test
    public void testNullSchemaWithIneffectiveConstraints() {

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // add the appender to the logger
        testLogger.addAppender(listAppender);

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue6491.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        String modelName = "NullWithIneffectiveValidations";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel cm = codegen.fromModel(modelName, sc);

        listAppender.stop();
        testLogger.detachAppender(listAppender);

        List<ILoggingEvent> logsList = new ArrayList<>(listAppender.list).stream()
                .filter(log -> Objects.equals(log.getThreadName(), Thread.currentThread().getName()))
                .collect(Collectors.toList());

        // JUnit assertions
        assertEquals(0, logsList.size(), "Messages: " + logsList.stream().map(ILoggingEvent::getMessage).collect(Collectors.toList()));
    }

    public static class FromParameter {
        private CodegenParameter codegenParameter(String path) {
            final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/fromParameter.yaml");
            new InlineModelResolver().flatten(openAPI);
            final DefaultCodegen codegen = new DefaultCodegen();
            codegen.setOpenAPI(openAPI);

            return codegen
                    .fromParameter(
                            openAPI
                                    .getPaths()
                                    .get(path)
                                    .getGet()
                                    .getParameters()
                                    .get(0),
                            new HashSet<>()
                    );
        }

        @Test
        public void setStyle() {
            CodegenParameter parameter = codegenParameter("/set_style");
            assertEquals("form", parameter.style);
        }

        @Test
        public void setShouldExplode() {
            CodegenParameter parameter = codegenParameter("/set_should_explode");
            assertTrue(parameter.isExplode);
        }

        @Test
        public void testConvertPropertyToBooleanAndWriteBack_Boolean_true() {
            final DefaultCodegen codegen = new DefaultCodegen();
            Map<String, Object> additionalProperties = codegen.additionalProperties();
            additionalProperties.put(CodegenConstants.SERIALIZABLE_MODEL, true);
            boolean result = codegen.convertPropertyToBooleanAndWriteBack(CodegenConstants.SERIALIZABLE_MODEL);
            assertTrue(result);
        }

        @Test
        public void testConvertPropertyToBooleanAndWriteBack_Boolean_false() {
            final DefaultCodegen codegen = new DefaultCodegen();
            Map<String, Object> additionalProperties = codegen.additionalProperties();
            additionalProperties.put(CodegenConstants.SERIALIZABLE_MODEL, false);
            boolean result = codegen.convertPropertyToBooleanAndWriteBack(CodegenConstants.SERIALIZABLE_MODEL);
            assertFalse(result);
        }

        @Test
        public void testConvertPropertyToBooleanAndWriteBack_String_true() {
            final DefaultCodegen codegen = new DefaultCodegen();
            Map<String, Object> additionalProperties = codegen.additionalProperties();
            additionalProperties.put(CodegenConstants.SERIALIZABLE_MODEL, "true");
            boolean result = codegen.convertPropertyToBooleanAndWriteBack(CodegenConstants.SERIALIZABLE_MODEL);
            assertTrue(result);
        }

        @Test
        public void testConvertPropertyToBooleanAndWriteBack_String_false() {
            final DefaultCodegen codegen = new DefaultCodegen();
            Map<String, Object> additionalProperties = codegen.additionalProperties();
            additionalProperties.put(CodegenConstants.SERIALIZABLE_MODEL, "false");
            boolean result = codegen.convertPropertyToBooleanAndWriteBack(CodegenConstants.SERIALIZABLE_MODEL);
            assertFalse(result);
        }

        @Test
        public void testConvertPropertyToBooleanAndWriteBack_String_blibb() {
            final DefaultCodegen codegen = new DefaultCodegen();
            Map<String, Object> additionalProperties = codegen.additionalProperties();
            additionalProperties.put(CodegenConstants.SERIALIZABLE_MODEL, "blibb");
            boolean result = codegen.convertPropertyToBooleanAndWriteBack(CodegenConstants.SERIALIZABLE_MODEL);
            assertFalse(result);
        }
    }

    class CodegenWithMultipleInheritance extends DefaultCodegen {
        public CodegenWithMultipleInheritance() {
            super();
            supportsInheritance = true;
            supportsMultipleInheritance = true;
        }
    }

    @Test
    public void testFromPropertyRequiredAndOptional() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_12857.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setDisallowAdditionalPropertiesIfNotPresent(false);

        String modelName = "FooRequired";
        Schema sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel fooRequired = codegen.fromModel(modelName, sc);
        modelName = "FooOptional";
        sc = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel fooOptional = codegen.fromModel(modelName, sc);
        assertTrue(fooRequired.vars.get(0).required);
        assertEquals("foo", fooRequired.vars.get(0).name);

        assertEquals(1, fooRequired.requiredVars.size());
        assertEquals("foo", fooRequired.requiredVars.get(0).name);
        assertTrue(fooRequired.requiredVars.get(0).required);

        assertFalse(fooOptional.vars.get(0).required);
        assertEquals("foo", fooOptional.vars.get(0).name);
        assertEquals(0, fooOptional.requiredVars.size());
    }

    @Test
    public void testAssigning310SpecWorks() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_1/petstore.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        assertEquals(openAPI, codegen.openAPI);
    }

    @Test
    public void testReferencedEnumType() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue-5676-enums.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        String modelName = "fakeRequestObjectWithReferencedEnum_request";

        Schema schemaWithReferencedEnum = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel modelWithReferencedSchema = codegen.fromModel(modelName, schemaWithReferencedEnum);
        CodegenProperty referencedEnumSchemaProperty = modelWithReferencedSchema.vars.get(1);

        Assertions.assertNotNull(schemaWithReferencedEnum);
        assertTrue(modelWithReferencedSchema.hasEnums);
        assertEquals("enumType", referencedEnumSchemaProperty.getName());
        assertFalse(referencedEnumSchemaProperty.isEnum);
        assertTrue(referencedEnumSchemaProperty.getIsEnumOrRef());
        assertTrue(referencedEnumSchemaProperty.isEnumRef);
        assertFalse(referencedEnumSchemaProperty.isInnerEnum);
        assertFalse(referencedEnumSchemaProperty.isString);
        assertFalse(referencedEnumSchemaProperty.isContainer);
        assertFalse(referencedEnumSchemaProperty.isPrimitiveType);
    }

    @Test
    public void testAllOfDefaultEnumType() {
        // test allOf with a single sub-schema and default value set in the top level
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue-5676-enums.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        String modelName = "EnumPatternObject";

        Schema schemaWithReferencedEnum = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel modelWithReferencedSchema = codegen.fromModel(modelName, schemaWithReferencedEnum);
        CodegenProperty defaultEnumSchemaProperty = modelWithReferencedSchema.vars.get(4);

        Assertions.assertNotNull(schemaWithReferencedEnum);
        assertTrue(modelWithReferencedSchema.hasEnums);
        assertEquals("defaultMinusnumberMinusenum", defaultEnumSchemaProperty.getName());
        assertFalse(defaultEnumSchemaProperty.isEnum);
        assertTrue(defaultEnumSchemaProperty.getIsEnumOrRef());
        assertTrue(defaultEnumSchemaProperty.isEnumRef);
        assertFalse(defaultEnumSchemaProperty.isInnerEnum);
        assertFalse(defaultEnumSchemaProperty.isString);
        assertFalse(defaultEnumSchemaProperty.isContainer);
        assertFalse(defaultEnumSchemaProperty.isPrimitiveType);
        assertEquals("2", defaultEnumSchemaProperty.defaultValue);

        // test allOf with a single sub-schema and no default value set in the top level
        CodegenProperty allOfEnumSchemaProperty = modelWithReferencedSchema.vars.get(5);
        assertEquals("allofMinusnumberMinusenum", allOfEnumSchemaProperty.getName());
        assertFalse(allOfEnumSchemaProperty.isEnum);
        assertTrue(allOfEnumSchemaProperty.getIsEnumOrRef());
        assertTrue(allOfEnumSchemaProperty.isEnumRef);
        assertFalse(allOfEnumSchemaProperty.isInnerEnum);
        assertFalse(allOfEnumSchemaProperty.isString);
        assertFalse(allOfEnumSchemaProperty.isContainer);
        assertFalse(allOfEnumSchemaProperty.isPrimitiveType);
        assertTrue(allOfEnumSchemaProperty.deprecated);
        assertEquals("null", allOfEnumSchemaProperty.defaultValue);
    }

    @Test
    public void testInlineEnumType() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue-5676-enums.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);
        String modelName = "fakeRequestObjectWithInlineEnum_request";

        Schema schemaWithReferencedEnum = openAPI.getComponents().getSchemas().get(modelName);
        CodegenModel modelWithReferencedSchema = codegen.fromModel(modelName, schemaWithReferencedEnum);
        CodegenProperty inlineEnumSchemaProperty = modelWithReferencedSchema.vars.get(1);

        Assertions.assertNotNull(schemaWithReferencedEnum);
        assertTrue(modelWithReferencedSchema.hasEnums);
        assertEquals("enumType", inlineEnumSchemaProperty.getName());
        assertTrue(inlineEnumSchemaProperty.isEnum);
        assertTrue(inlineEnumSchemaProperty.isInnerEnum);
        assertFalse(inlineEnumSchemaProperty.isEnumRef);
        assertTrue(inlineEnumSchemaProperty.getIsEnumOrRef());
        assertTrue(inlineEnumSchemaProperty.isString);
        assertFalse(inlineEnumSchemaProperty.isContainer);
        assertFalse(inlineEnumSchemaProperty.isPrimitiveType);
    }

    @Test
    public void testAddOption() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.addOption("optionKey", "optionDesc", "defaultValue", Map.of("defaultValue", "defaultDesc"));

        CliOption expected = new CliOption("optionKey", "optionDesc");
        expected.setDefault("defaultValue");
        expected.setEnum(Map.of("defaultValue", "defaultDesc"));

        assertTrue(codegen.cliOptions.contains(expected));
    }

    @Test
    public void testAddOptionDefaultNull() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.addOption("optionKey", "optionDesc", null);

        CliOption expected = new CliOption("optionKey", "optionDesc");

        assertTrue(codegen.cliOptions.contains(expected));
    }

    @Test
    public void testAddOptionEnumValuesNull() {
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.addOption("optionKey", "optionDesc", "defaultValue");

        CliOption expected = new CliOption("optionKey", "optionDesc");
        expected.setDefault("defaultValue");

        assertTrue(codegen.cliOptions.contains(expected));
    }

    @Test
    public void testRequestInlineSingleExample() {
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/content-data.yaml");
        codegen.setOpenAPI(openAPI);
        String path;
        CodegenOperation co;

        path = "/inlineRequestBodySchemasDifferingByContentType";
        co = codegen.fromOperation(path, "POST", openAPI.getPaths().get(path).getPost(), null);
        CodegenParameter bodyParameter = co.bodyParam;
        LinkedHashMap<String, CodegenMediaType> content = bodyParameter.getContent();
        assertNotNull(content);
        CodegenMediaType mt = content.get(APP_JSON);
        assertNotNull(mt.getExample());
    }

    @Test
    void testIsXML() {
        final DefaultCodegen codegen = new DefaultCodegen();
        assertTrue(codegen.isXmlMimeType(APP_XML));
        assertTrue(codegen.isXmlMimeType("application/rss+xml"));
    }

    @Test
    public void testWebhooks() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_1/webhooks.yaml");
        final DefaultCodegen codegen = new DefaultCodegen();
        codegen.setOpenAPI(openAPI);

        Operation operation = openAPI.getWebhooks().get("newPet").getPost();
        CodegenOperation co = codegen.fromOperation("newPet", "get", operation, null);

        assertEquals("/newPet", co.path);
        assertEquals("newPetGet", co.operationId);
    }

    @Test
    public void testAllVars_issue_18340() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/spring/issue_18340.yaml");
        new OpenAPINormalizer(openAPI, Map.of("REFACTOR_ALLOF_WITH_PROPERTIES_ONLY", "true"))
                .normalize();
        Schema catModel = ModelUtils.getSchema(openAPI, "Cat");
        Schema cat2Model = ModelUtils.getSchema(openAPI, "Cat2");
        DefaultCodegen defaultCodegen = new DefaultCodegen();
        defaultCodegen.setOpenAPI(openAPI);
        CodegenModel defaultCat = defaultCodegen.fromModel("Cat", catModel);
        assertThat(getNames(defaultCat.allVars)).isEqualTo(List.of("petType", "name"));

        // the normalizer makes it work even for generators supporting inheritance
        SpringCodegen springCodegen = new SpringCodegen();
        springCodegen.setOpenAPI(openAPI);
        CodegenModel springCat = springCodegen.fromModel("Cat", catModel);
        assertThat(getNames(springCat.allVars)).isEqualTo(List.of("petType", "name"));
        CodegenModel springCat2 = springCodegen.fromModel("Cat2", cat2Model);
        assertThat(getNames(springCat2.allVars)).isEqualTo(List.of("petType", "name"));
    }

    @Test
    public void testMultipleRequestParameter_hasSingleParamFalse() {
        // Given
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/content-data.yaml");
        codegen.setOpenAPI(openAPI);
        String path = "/jsonQueryParams";
        CodegenOperation codegenOperation = codegen.fromOperation(path, "GET", openAPI.getPaths().get(path).getGet(), null);

        // When & Then
        assertThat(codegenOperation.getHasSingleParam()).isFalse();
    }

    @Test
    public void testSingleRequestParameter_hasSingleParamTrue() {
        // Given
        DefaultCodegen codegen = new DefaultCodegen();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/petstore-with-fake-endpoints-models-for-testing.yaml");
        codegen.setOpenAPI(openAPI);
        String path = "/fake/inline-additionalProperties";
        CodegenOperation codegenOperation = codegen.fromOperation(path, "POST", openAPI.getPaths().get(path).getPost(), null);

        // When & Then
        assertThat(codegenOperation.getHasSingleParam()).isTrue();
    }
}
