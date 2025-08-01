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

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.DocumentationFeature;
import org.openapitools.codegen.meta.features.GlobalFeature;
import org.openapitools.codegen.meta.features.SecurityFeature;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.SemVer;
import org.openapitools.codegen.utils.YamlConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;
import static org.openapitools.codegen.utils.StringUtils.*;

public class TypeScriptAngularClientCodegen extends AbstractTypeScriptClientCodegen {
    private final Logger LOGGER = LoggerFactory.getLogger(TypeScriptAngularClientCodegen.class);

    private static String CLASS_NAME_PREFIX_PATTERN = "^[a-zA-Z0-9]*$";
    private static String CLASS_NAME_SUFFIX_PATTERN = "^[a-zA-Z0-9]*$";
    private static String FILE_NAME_SUFFIX_PATTERN = "^[a-zA-Z0-9.-]*$";

    public static enum QUERY_PARAM_OBJECT_FORMAT_TYPE {dot, json, key}

    public static enum PROVIDED_IN_LEVEL {none, root, any, platform}

    private static final String DEFAULT_IMPORT_PREFIX = "./";
    private static final String DEFAULT_MODEL_IMPORT_DIRECTORY_PREFIX = "../";

    public static final String NPM_REPOSITORY = "npmRepository";
    public static final String WITH_INTERFACES = "withInterfaces";
    public static final String USE_SINGLE_REQUEST_PARAMETER = "useSingleRequestParameter";
    public static final String TAGGED_UNIONS = "taggedUnions";
    public static final String NG_VERSION = "ngVersion";
    public static final String PROVIDED_IN = "providedIn";
    public static final String ENFORCE_GENERIC_MODULE_WITH_PROVIDERS = "enforceGenericModuleWithProviders";
    public static final String HTTP_CONTEXT_IN_OPTIONS = "httpContextInOptions";
    public static final String HTTP_TRANSFER_CACHE_IN_OPTIONS = "httpTransferCacheInOptions";
    public static final String API_MODULE_PREFIX = "apiModulePrefix";
    public static final String CONFIGURATION_PREFIX = "configurationPrefix";
    public static final String SERVICE_SUFFIX = "serviceSuffix";
    public static final String SERVICE_FILE_SUFFIX = "serviceFileSuffix";
    public static final String MODEL_SUFFIX = "modelSuffix";
    public static final String MODEL_FILE_SUFFIX = "modelFileSuffix";
    public static final String FILE_NAMING = "fileNaming";
    public static final String STRING_ENUMS = "stringEnums";
    public static final String STRING_ENUMS_DESC = "Generate string enums instead of objects for enum values.";
    public static final String QUERY_PARAM_OBJECT_FORMAT = "queryParamObjectFormat";
    public static final String USE_SQUARE_BRACKETS_IN_ARRAY_NAMES = "useSquareBracketsInArrayNames";
    public static final String TS_VERSION = "tsVersion";
    public static final String RXJS_VERSION = "rxjsVersion";
    public static final String NGPACKAGR_VERSION = "ngPackagrVersion";
    public static final String ZONEJS_VERSION = "zonejsVersion";

    protected String ngVersion = "20.0.0";
    @Getter @Setter
    protected String npmRepository = null;
    @Setter(AccessLevel.PRIVATE) private boolean useSingleRequestParameter = false;
    protected String serviceSuffix = "Service";
    protected String serviceFileSuffix = ".service";
    protected String modelSuffix = "";
    protected String modelFileSuffix = "";
    protected String fileNaming = "camelCase";
    @Getter protected Boolean stringEnums = false;
    protected QUERY_PARAM_OBJECT_FORMAT_TYPE queryParamObjectFormat = QUERY_PARAM_OBJECT_FORMAT_TYPE.dot;
    protected PROVIDED_IN_LEVEL providedIn = PROVIDED_IN_LEVEL.root;

    private boolean taggedUnions = false;

    public TypeScriptAngularClientCodegen() {
        super();

        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .includeSecurityFeatures(SecurityFeature.BearerToken)
                .includeGlobalFeatures(GlobalFeature.ParameterStyling)
        );

        this.outputFolder = "generated-code/typescript-angular";

        supportsMultipleInheritance = true;

        embeddedTemplateDir = templateDir = "typescript-angular";
        modelTemplateFiles.put("model.mustache", ".ts");
        apiTemplateFiles.put("api.service.mustache", ".ts");
        languageSpecificPrimitives.add("Blob");
        typeMapping.put("file", "Blob");
        apiPackage = "api";
        modelPackage = "model";

        this.cliOptions.add(new CliOption(NPM_REPOSITORY,
                "Use this property to set an url your private npmRepo in the package.json"));
        this.cliOptions.add(CliOption.newBoolean(WITH_INTERFACES,
                "Setting this property to true will generate interfaces next to the default class implementations.",
                false));
        this.cliOptions.add(CliOption.newBoolean(USE_SINGLE_REQUEST_PARAMETER,
                "Setting this property to true will generate functions with a single argument containing all API endpoint parameters instead of one argument per parameter.",
                false));
        this.cliOptions.add(CliOption.newBoolean(TAGGED_UNIONS,
                "Use discriminators to create tagged unions instead of extending interfaces.",
                this.taggedUnions));
        CliOption providedInCliOpt = new CliOption(PROVIDED_IN,
                "Use this property to provide Injectables in wanted level.").defaultValue("root");
        Map<String, String> providedInOptions = new HashMap<>();
        providedInOptions.put(PROVIDED_IN_LEVEL.none.toString(), "No providedIn)");
        providedInOptions.put(PROVIDED_IN_LEVEL.root.toString(), "The application-level injector in most apps.");
        providedInOptions.put(PROVIDED_IN_LEVEL.platform.toString(), "A special singleton platform injector shared by all applications on the page.");
        providedInOptions.put(PROVIDED_IN_LEVEL.any.toString(), "Provides a unique instance in each lazy loaded module while all eagerly loaded modules share one instance.");
        providedInCliOpt.setEnum(providedInOptions);
        this.cliOptions.add(providedInCliOpt);
        this.cliOptions.add(new CliOption(NG_VERSION, "The version of Angular. (At least 9.0.0)").defaultValue(this.ngVersion));
        this.cliOptions.add(new CliOption(API_MODULE_PREFIX, "The prefix of the generated ApiModule."));
        this.cliOptions.add(new CliOption(CONFIGURATION_PREFIX, "The prefix of the generated Configuration."));
        this.cliOptions.add(new CliOption(SERVICE_SUFFIX, "The suffix of the generated service.").defaultValue(this.serviceSuffix));
        this.cliOptions.add(new CliOption(SERVICE_FILE_SUFFIX, "The suffix of the file of the generated service (service<suffix>.ts).").defaultValue(this.serviceFileSuffix));
        this.cliOptions.add(new CliOption(MODEL_SUFFIX, "The suffix of the generated model."));
        this.cliOptions.add(new CliOption(MODEL_FILE_SUFFIX, "The suffix of the file of the generated model (model<suffix>.ts)."));
        this.cliOptions.add(new CliOption(FILE_NAMING, "Naming convention for the output files: 'camelCase', 'kebab-case'.").defaultValue(this.fileNaming));
        this.cliOptions.add(new CliOption(STRING_ENUMS, STRING_ENUMS_DESC).defaultValue(String.valueOf(this.stringEnums)));
        this.cliOptions.add(new CliOption(QUERY_PARAM_OBJECT_FORMAT, "The format for query param objects: 'dot', 'json', 'key'.").defaultValue(this.queryParamObjectFormat.name()));
        this.cliOptions.add(CliOption.newBoolean(USE_SQUARE_BRACKETS_IN_ARRAY_NAMES, "Setting this property to true will add brackets to array attribute names, e.g. my_values[].", false));
        this.cliOptions.add(new CliOption(TS_VERSION, "The version of typescript compatible with Angular (see ngVersion option)."));
        this.cliOptions.add(new CliOption(RXJS_VERSION, "The version of RxJS compatible with Angular (see ngVersion option)."));
        this.cliOptions.add(new CliOption(NGPACKAGR_VERSION, "The version of ng-packagr compatible with Angular (see ngVersion option)."));
        this.cliOptions.add(new CliOption(ZONEJS_VERSION, "The version of zone.js compatible with Angular (see ngVersion option)."));
    }

    @Override
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
        codegenModel.additionalPropertiesType = getTypeDeclaration(ModelUtils.getAdditionalProperties(schema));
        addImport(codegenModel, codegenModel.additionalPropertiesType);
    }

    @Override
    public String getName() {
        return "typescript-angular";
    }

    @Override
    public String getHelp() {
        return "Generates a TypeScript Angular (9.x - 20.x) client library.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        // determine NG version
        SemVer ngVersion;
        if (additionalProperties.containsKey(NG_VERSION)) {
            ngVersion = new SemVer(additionalProperties.get(NG_VERSION).toString());
        } else {
            ngVersion = new SemVer(this.ngVersion);
            LOGGER.info("generating code for Angular {} ...", ngVersion);
            LOGGER.info("  (you can select the angular version by setting the additionalProperties (--additional-properties in CLI) ngVersion)");
        }
        boolean ngVersionAtLeast_17 = ngVersion.atLeast("17.0.0");

        supportingFiles.add(
                new SupportingFile("models.mustache", modelPackage().replace('.', File.separatorChar), "models.ts"));
        supportingFiles
                .add(new SupportingFile("apis.mustache", apiPackage().replace('.', File.separatorChar), "api.ts"));
        supportingFiles.add(new SupportingFile("index.mustache", getIndexDirectory(), "index.ts"));
        supportingFiles.add(new SupportingFile("api.module.mustache", getIndexDirectory(), "api.module.ts"));
        if (ngVersionAtLeast_17) {
            supportingFiles.add(new SupportingFile("provide-api.mustache", getIndexDirectory(), "provide-api.ts"));
        }
        supportingFiles.add(new SupportingFile("configuration.mustache", getIndexDirectory(), "configuration.ts"));
        supportingFiles.add(new SupportingFile("api.base.service.mustache", getIndexDirectory(), "api.base.service.ts"));
        supportingFiles.add(new SupportingFile("variables.mustache", getIndexDirectory(), "variables.ts"));
        supportingFiles.add(new SupportingFile("encoder.mustache", getIndexDirectory(), "encoder.ts"));
        supportingFiles.add(new SupportingFile("param.mustache", getIndexDirectory(), "param.ts"));
        supportingFiles.add(new SupportingFile("gitignore", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));

        if(ngVersionAtLeast_17) {
            supportingFiles.add(new SupportingFile("README.mustache", getIndexDirectory(), "README.md"));
        }
        else {
            supportingFiles.add(new SupportingFile("README_beforeV17.mustache", getIndexDirectory(), "README.md"));
        }


        if (!ngVersion.atLeast("9.0.0")) {
            throw new IllegalArgumentException("Invalid ngVersion: " + ngVersion + ". Only Angular v9+ is supported.");
        }

        if (additionalProperties.containsKey(NPM_NAME)) {
            addNpmPackageGeneration(ngVersion);
        }

        if (additionalProperties.containsKey(STRING_ENUMS)) {
            setStringEnums(Boolean.parseBoolean(additionalProperties.get(STRING_ENUMS).toString()));
            additionalProperties.put("stringEnums", getStringEnums());
            if (getStringEnums()) {
                classEnumSeparator = "";
            }
        }

        if (additionalProperties.containsKey(WITH_INTERFACES)) {
            boolean withInterfaces = Boolean.parseBoolean(additionalProperties.get(WITH_INTERFACES).toString());
            if (withInterfaces) {
                apiTemplateFiles.put("apiInterface.mustache", "Interface.ts");
            }
        }

        if (additionalProperties.containsKey(USE_SINGLE_REQUEST_PARAMETER)) {
            this.setUseSingleRequestParameter(convertPropertyToBoolean(USE_SINGLE_REQUEST_PARAMETER));
        }
        writePropertyBack(USE_SINGLE_REQUEST_PARAMETER, getUseSingleRequestParameter());

        if (additionalProperties.containsKey(TAGGED_UNIONS)) {
            taggedUnions = Boolean.parseBoolean(additionalProperties.get(TAGGED_UNIONS).toString());
        }

        if (additionalProperties.containsKey(PROVIDED_IN)) {
            setProvidedIn(additionalProperties.get(PROVIDED_IN).toString());
        }
        additionalProperties.put("providedIn", providedIn);
        additionalProperties.put("isProvidedInNone", getIsProvidedInNone());

        additionalProperties.put(ENFORCE_GENERIC_MODULE_WITH_PROVIDERS, true);

        if (ngVersion.atLeast("12.0.0")) {
            additionalProperties.put(HTTP_CONTEXT_IN_OPTIONS, true);
        }

        if (ngVersion.atLeast("17.0.0")) {
            additionalProperties.put(HTTP_TRANSFER_CACHE_IN_OPTIONS, true);
        }

        additionalProperties.put(NG_VERSION, ngVersion);
        additionalProperties.put("ngVersionAtLeast_17", ngVersionAtLeast_17);

        if (additionalProperties.containsKey(API_MODULE_PREFIX)) {
            String apiModulePrefix = additionalProperties.get(API_MODULE_PREFIX).toString();
            validateClassPrefixArgument("ApiModule", apiModulePrefix);

            additionalProperties.put("apiModuleClassName", apiModulePrefix + "ApiModule");
        } else {
            additionalProperties.put("apiModuleClassName", "ApiModule");
        }
        if (additionalProperties.containsKey(CONFIGURATION_PREFIX)) {
            String configurationPrefix = additionalProperties.get(CONFIGURATION_PREFIX).toString();
            validateClassPrefixArgument("Configuration", configurationPrefix);

            additionalProperties.put("configurationClassName", configurationPrefix + "Configuration");
            additionalProperties.put("configurationParametersInterfaceName", configurationPrefix + "ConfigurationParameters");
        } else {
            additionalProperties.put("configurationClassName", "Configuration");
            additionalProperties.put("configurationParametersInterfaceName", "ConfigurationParameters");
        }
        if (additionalProperties.containsKey(SERVICE_SUFFIX)) {
            serviceSuffix = additionalProperties.get(SERVICE_SUFFIX).toString();
            validateClassSuffixArgument("Service", serviceSuffix);
        }
        if (additionalProperties.containsKey(SERVICE_FILE_SUFFIX)) {
            serviceFileSuffix = additionalProperties.get(SERVICE_FILE_SUFFIX).toString();
            validateFileSuffixArgument("Service", serviceFileSuffix);
        }
        if (additionalProperties.containsKey(MODEL_SUFFIX)) {
            modelSuffix = additionalProperties.get(MODEL_SUFFIX).toString();
            validateClassSuffixArgument("Model", modelSuffix);
        }
        if (additionalProperties.containsKey(MODEL_FILE_SUFFIX)) {
            modelFileSuffix = additionalProperties.get(MODEL_FILE_SUFFIX).toString();
            validateFileSuffixArgument("Model", modelFileSuffix);
        }
        if (additionalProperties.containsKey(FILE_NAMING)) {
            this.setFileNaming(additionalProperties.get(FILE_NAMING).toString());
        }

        if (additionalProperties.containsKey(QUERY_PARAM_OBJECT_FORMAT)) {
            setQueryParamObjectFormat((String) additionalProperties.get(QUERY_PARAM_OBJECT_FORMAT));
        }
        additionalProperties.put("isQueryParamObjectFormatDot", getQueryParamObjectFormatDot());
        additionalProperties.put("isQueryParamObjectFormatJson", getQueryParamObjectFormatJson());
        additionalProperties.put("isQueryParamObjectFormatKey", getQueryParamObjectFormatKey());

    }

    @Data
    static class AngularDependencies {
        String tsVersion;
        String rxjsVersion;
        String ngPackagrVersion;
        String zonejsVersion;
        String tsickleVersion;
    }

    private void addNpmPackageGeneration(SemVer ngVersion) {

        if (additionalProperties.containsKey(NPM_REPOSITORY)) {
            this.setNpmRepository(additionalProperties.get(NPM_REPOSITORY).toString());
        }

        Map<String, AngularDependencies> angularDependenciesByVersion = YamlConfigUtils.loadAsMap("typescript-angular/angularDependenciesByVersion.yaml", AngularDependencies.class);

        AngularDependencies angularDependencies = angularDependenciesByVersion.entrySet().stream()
                // we filter only config version above or equal the current one
                .filter(versionMatrix -> ngVersion.atLeast(versionMatrix.getKey()))
                // get can the latest version configured that match the current one
                .max(Comparator.comparing(s -> new SemVer(s.getKey())))
                .map(Map.Entry::getValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ngVersion. Only Angular v9+ is supported."));

        if (!additionalProperties.containsKey(TS_VERSION)) {
            additionalProperties.put(TS_VERSION, angularDependencies.getTsVersion());
        }

        if (!additionalProperties.containsKey(RXJS_VERSION)) {
            additionalProperties.put(RXJS_VERSION, angularDependencies.getRxjsVersion());
        }

        if (!additionalProperties.containsKey(NGPACKAGR_VERSION)) {
            additionalProperties.put(NGPACKAGR_VERSION, angularDependencies.getNgPackagrVersion());
        }

        if (!additionalProperties.containsKey(ZONEJS_VERSION)) {
            additionalProperties.put(ZONEJS_VERSION, angularDependencies.getZonejsVersion());
        }

        if (angularDependencies.getTsickleVersion() != null) {
            additionalProperties.put("tsickleVersion", angularDependencies.getTsickleVersion());
        }

        //Files for building our lib
        supportingFiles.add(new SupportingFile("ng-package.mustache", getIndexDirectory(), "ng-package.json"));
        supportingFiles.add(new SupportingFile("package.mustache", getIndexDirectory(), "package.json"));
        supportingFiles.add(new SupportingFile("tsconfig.mustache", getIndexDirectory(), "tsconfig.json"));
    }

    private String getIndexDirectory() {
        String indexPackage = modelPackage.substring(0, Math.max(0, modelPackage.lastIndexOf('.')));
        return indexPackage.replace('.', File.separatorChar);
    }

    public void setStringEnums(boolean value) {
        stringEnums = value;
    }

    public boolean getQueryParamObjectFormatDot() {
        return QUERY_PARAM_OBJECT_FORMAT_TYPE.dot.equals(queryParamObjectFormat);
    }

    public boolean getQueryParamObjectFormatJson() {
        return QUERY_PARAM_OBJECT_FORMAT_TYPE.json.equals(queryParamObjectFormat);
    }

    public boolean getQueryParamObjectFormatKey() {
        return QUERY_PARAM_OBJECT_FORMAT_TYPE.key.equals(queryParamObjectFormat);
    }

    @Override
    public boolean isDataTypeFile(final String dataType) {
        return "Blob".equals(dataType);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isFileSchema(p)) {
            return "Blob";
        } else {
            return super.getTypeDeclaration(p);
        }
    }


    private String applyLocalTypeMapping(String type) {
        if (typeMapping.containsKey(type)) {
            type = typeMapping.get(type);
        }
        return type;
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);
        parameter.dataType = applyLocalTypeMapping(parameter.dataType);
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap operations, List<ModelMap> allModels) {
        OperationMap objs = operations.getOperations();

        // Add filename information for api imports
        objs.put("apiFilename", getApiFilenameFromClassname(objs.getClassname()));

        List<CodegenOperation> ops = objs.getOperation();
        boolean hasSomeFormParams = false;
        boolean hasSomeEncodableParams = false;
        for (CodegenOperation op : ops) {
            if (op.getHasFormParams()) {
                hasSomeFormParams = true;
            }
            op.httpMethod = op.httpMethod.toLowerCase(Locale.ENGLISH);
            // deduplicate auth methods by name (as they will lead to duplicate code):
            op.authMethods =
                    op.authMethods != null ? op.authMethods.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(x -> x.name))), ArrayList::new))
                            : null;

            // Prep a string buffer where we're going to set up our new version of the string.
            StringBuilder pathBuffer = new StringBuilder();
            ParameterExpander paramExpander = new ParameterExpander(op, this::toParamName);
            int insideCurly = 0;

            // Iterate through existing string, one character at a time.
            for (int i = 0; i < op.path.length(); i++) {
                switch (op.path.charAt(i)) {
                    case '{':
                        // We entered curly braces, so track that.
                        insideCurly++;
                        break;
                    case '}':
                        // We exited curly braces, so track that.
                        insideCurly--;

                        pathBuffer.append(paramExpander.buildPathEntry());
                        hasSomeEncodableParams = true;
                        break;
                    default:
                        char nextChar = op.path.charAt(i);
                        if (insideCurly > 0) {
                            paramExpander.appendToParameterName(nextChar);
                        } else {
                            pathBuffer.append(nextChar);
                        }
                        break;
                }
            }

            // Overwrite path to TypeScript template string, after applying everything we just did.
            op.path = pathBuffer.toString();
        }

        operations.put("hasSomeFormParams", hasSomeFormParams);
        operations.put("hasSomeEncodableParams", hasSomeEncodableParams);

        // Add additional filename information for model imports in the services
        List<Map<String, String>> imports = operations.getImports();
        for (Map<String, String> im : imports) {
            // This property is not used in the templates any more, subject for removal
            im.put("filename", im.get("import"));
            im.put("classname", im.get("classname"));
        }

        return operations;
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        ModelsMap result = super.postProcessModels(objs);
        return postProcessModelsEnum(result);
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap entry : result.values()) {
            for (ModelMap mo : entry.getModels()) {
                CodegenModel cm = mo.getModel();
                if (taggedUnions) {
                    mo.put(TAGGED_UNIONS, true);
                    if (cm.discriminator != null && cm.children != null) {
                        for (CodegenModel child : cm.children) {
                            cm.imports.add(child.classname);
                            setChildDiscriminatorValue(cm, child);
                        }
                    }

                    // with tagged union, a child model doesn't extend the parent (all properties are just copied over)
                    // it means we don't need to import that parent any more
                    if (cm.parent != null) {
                        cm.imports.remove(cm.parent);

                        // however, it's possible that the child model contains a recursive reference to the parent
                        // in order to support this case, we update the list of imports from properties once again
                        for (CodegenProperty cp : cm.allVars) {
                            addImportsForPropertyType(cm, cp);
                        }
                        removeSelfReferenceImports(cm);

                    }
                }
                // Add additional filename information for imports
                Set<String> parsedImports = parseImports(cm);
                mo.put("tsImports", toTsImports(cm, parsedImports));
            }
        }
        return result;
    }

    private void setChildDiscriminatorValue(CodegenModel parent, CodegenModel child) {
        if (
                child.vendorExtensions.isEmpty() ||
                        !child.vendorExtensions.containsKey("x-discriminator-value")
        ) {

            for (CodegenProperty prop : child.allVars) {
                if (prop.baseName.equals(parent.discriminator.getPropertyName())) {

                    for (CodegenDiscriminator.MappedModel mappedModel : parent.discriminator.getMappedModels()) {
                        if (mappedModel.getModelName().equals(child.classname)) {
                            prop.discriminatorValue = mappedModel.getMappingName();
                        }
                    }
                }
            }
        }
    }

    /**
     * Parse imports
     */
    private Set<String> parseImports(CodegenModel cm) {
        Set<String> newImports = new HashSet<>();
        if (cm.imports.size() > 0) {
            for (String name : cm.imports) {
                if (name.indexOf(" | ") >= 0) {
                    String[] parts = name.split(" \\| ");
                    Collections.addAll(newImports, parts);
                } else {
                    newImports.add(name);
                }
            }
        }
        return newImports;
    }

    private List<Map<String, String>> toTsImports(CodegenModel cm, Set<String> imports) {
        List<Map<String, String>> tsImports = new ArrayList<>();
        for (String im : imports) {
            if (!im.equals(cm.classname)) {
                HashMap<String, String> tsImport = new HashMap<>();
                // TVG: This is used as class name in the import statements of the model file
                tsImport.put("classname", im);
                tsImport.put("filename", toModelFilename(removeModelPrefixSuffix(im)));
                tsImports.add(tsImport);
            }
        }
        return tsImports;
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultService";
        }
        return camelize(name) + serviceSuffix;
    }

    @Override
    public String toApiFilename(String name) {
        if (name.length() == 0) {
            return "default.service";
        }
        return this.convertUsingFileNamingConvention(name) + serviceFileSuffix;
    }

    @Override
    public String toApiImport(String name) {
        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        }
        return apiPackage() + "/" + toApiFilename(name);
    }

    @Override
    public String toModelFilename(String name) {
        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        }
        return DEFAULT_IMPORT_PREFIX + this.convertUsingFileNamingConvention(super.toModelFilename(name)) + modelFileSuffix;
    }

    @Override
    public String toModelImport(String name) {
        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        }
        return DEFAULT_MODEL_IMPORT_DIRECTORY_PREFIX + modelPackage() + "/" + toModelFilename(removeModelPrefixSuffix(name)).substring(DEFAULT_IMPORT_PREFIX.length());
    }

    private boolean getUseSingleRequestParameter() {
        return useSingleRequestParameter;
    }

    private String getApiFilenameFromClassname(String classname) {
        String name = classname.substring(0, classname.length() - serviceSuffix.length());
        return toApiFilename(name);
    }

    @Override
    public String toModelName(String name) {
        name = addSuffix(name, modelSuffix);
        return super.toModelName(name);
    }

    public String removeModelPrefixSuffix(String name) {
        String result = name;
        if (modelSuffix.length() > 0 && result.endsWith(modelSuffix)) {
            result = result.substring(0, result.length() - modelSuffix.length());
        }
        String prefix = capitalize(this.modelNamePrefix);
        String suffix = capitalize(this.modelNameSuffix);
        if (prefix.length() > 0 && result.startsWith(prefix)) {
            result = result.substring(prefix.length());
        }
        if (suffix.length() > 0 && result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }

        return result;
    }

    /**
     * Validates that the given string value only contains '-', '.' and alpha numeric characters.
     * Throws an IllegalArgumentException, if the string contains any other characters.
     *
     * @param argument The name of the argument being validated. This is only used for displaying an error message.
     * @param value    The value that is being validated.
     */
    private void validateFileSuffixArgument(String argument, String value) {
        if (!value.matches(FILE_NAME_SUFFIX_PATTERN)) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s file suffix only allows '.', '-' and alphanumeric characters.", argument)
            );
        }
    }

    /**
     * Validates that the given string value only contains alpha numeric characters.
     * Throws an IllegalArgumentException, if the string contains any other characters.
     *
     * @param argument The name of the argument being validated. This is only used for displaying an error message.
     * @param value    The value that is being validated.
     */
    private void validateClassPrefixArgument(String argument, String value) {
        if (!value.matches(CLASS_NAME_PREFIX_PATTERN)) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s class prefix only allows alphanumeric characters.", argument)
            );
        }
    }

    /**
     * Validates that the given string value only contains alpha numeric characters.
     * Throws an IllegalArgumentException, if the string contains any other characters.
     *
     * @param argument The name of the argument being validated. This is only used for displaying an error message.
     * @param value    The value that is being validated.
     */
    private void validateClassSuffixArgument(String argument, String value) {
        if (!value.matches(CLASS_NAME_SUFFIX_PATTERN)) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s class suffix only allows alphanumeric characters.", argument)
            );
        }
    }

    /**
     * Set the query param object format.
     *
     * @param format the query param object format to use
     */
    public void setQueryParamObjectFormat(String format) {
        try {
            queryParamObjectFormat = QUERY_PARAM_OBJECT_FORMAT_TYPE.valueOf(format);
        } catch (IllegalArgumentException e) {
            String values = Stream.of(QUERY_PARAM_OBJECT_FORMAT_TYPE.values())
                    .map(value -> "'" + value.name() + "'")
                    .collect(Collectors.joining(", "));

            String msg = String.format(Locale.ROOT, "Invalid query param object format '%s'. Must be one of %s.", format, values);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Set the file naming type.
     *
     * @param fileNaming the file naming to use
     */
    private void setFileNaming(String fileNaming) {
        if ("camelCase".equals(fileNaming) || "kebab-case".equals(fileNaming)) {
            this.fileNaming = fileNaming;
        } else {
            throw new IllegalArgumentException("Invalid file naming '" +
                    fileNaming + "'. Must be 'camelCase' or 'kebab-case'");
        }
    }

    /**
     * Converts the original name according to the current <code>fileNaming</code> strategy.
     *
     * @param originalName the original name to transform
     * @return the transformed name
     */
    private String convertUsingFileNamingConvention(String originalName) {
        String name = this.removeModelPrefixSuffix(originalName);
        if ("kebab-case".equals(fileNaming)) {
            name = dashize(underscore(name));
        } else {
            name = camelize(name, LOWERCASE_FIRST_LETTER);
        }
        return name;
    }

    /**
     * Set the Injectable level
     *
     * @param level the wanted level
     */
    public void setProvidedIn(String level) {
        try {
            providedIn = PROVIDED_IN_LEVEL.valueOf(level);
        } catch (IllegalArgumentException e) {
            String values = Stream.of(PROVIDED_IN_LEVEL.values())
                    .map(value -> "'" + value.name() + "'")
                    .collect(Collectors.joining(", "));

            String msg = String.format(Locale.ROOT, "Invalid providedIn level '%s'. Must be one of %s.", level, values);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     *
     */
    private boolean getIsProvidedInNone() {
        return PROVIDED_IN_LEVEL.none.equals(providedIn);
    }
}
