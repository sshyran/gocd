/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.config;

import com.thoughtworks.go.config.exceptions.EntityType;
import com.thoughtworks.go.config.exceptions.RecordNotFoundException;
import com.thoughtworks.go.config.merge.MergeEnvironmentConfig;
import com.thoughtworks.go.config.remote.FileConfigOrigin;
import com.thoughtworks.go.config.remote.RepoConfigOrigin;
import com.thoughtworks.go.domain.ConfigErrors;
import com.thoughtworks.go.domain.EnvironmentPipelineMatcher;
import com.thoughtworks.go.helper.GoConfigMother;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.*;

public class EnvironmentsConfigTest {
    private EnvironmentsConfig envsConfig;
    private BasicEnvironmentConfig basicEnvConfig;

    @BeforeEach
    public void setUp() throws Exception {
        envsConfig = new EnvironmentsConfig();

        basicEnvConfig = new BasicEnvironmentConfig(new CaseInsensitiveString("uat"));
        basicEnvConfig.addPipeline(new CaseInsensitiveString("deployment"));
        basicEnvConfig.addAgent("agent-one");

        envsConfig.add(basicEnvConfig);
    }

    @Test
    public void shouldAddPipelinesToEnvironment() {
        String prodPipelineName = "production";
        String stagePipelineName = "stage";

        String nonExistingEnv = "env-that-does-not-exist";
        envsConfig.addPipelinesToEnvironment(nonExistingEnv, prodPipelineName, stagePipelineName);

        CaseInsensitiveString prodPipelineEnv = envsConfig.findEnvironmentNameForPipeline(new CaseInsensitiveString(prodPipelineName));
        CaseInsensitiveString stagePipelineEnv = envsConfig.findEnvironmentNameForPipeline(new CaseInsensitiveString(stagePipelineName));

        assertThat(prodPipelineEnv.toString(), equalTo(nonExistingEnv));
        assertThat(stagePipelineEnv.toString(), equalTo(nonExistingEnv));
    }

    @Test
    public void shouldAddPipelinesToNonExistingEnvironment() {
        envsConfig.addPipelinesToEnvironment("uat", "production", "stage");
        CaseInsensitiveString prodPipelineEnv = envsConfig.findEnvironmentNameForPipeline(new CaseInsensitiveString("production"));
        CaseInsensitiveString stagePipelineEnv = envsConfig.findEnvironmentNameForPipeline(new CaseInsensitiveString("stage"));
        assertThat(prodPipelineEnv.toString(), equalTo("uat"));
        assertThat(stagePipelineEnv.toString(), equalTo("uat"));
    }

    @Test
    public void shouldFindFirstEnvironmentPipelineMatcherForPipeline() {
        BasicEnvironmentConfig testEnvConfig = new BasicEnvironmentConfig(new CaseInsensitiveString("test"));

        String deploymentPipeline = "deployment";

        testEnvConfig.addPipeline(new CaseInsensitiveString(deploymentPipeline));
        testEnvConfig.addAgent("agent-two");
        envsConfig.add(testEnvConfig);

        EnvironmentPipelineMatcher matcher = envsConfig.matchersForPipeline(deploymentPipeline);

        assertNotNull(matcher);
        assertTrue(matcher.hasPipeline(deploymentPipeline));
        assertTrue(matcher.match(deploymentPipeline, "agent-one"));
        assertThat(matcher.name().toString(), equalTo("uat"));
    }

    @Test
    public void shouldFindNullEnvironmentPipelineMatcherForNotExistingPipeline() {
        BasicEnvironmentConfig testEnvConfig = new BasicEnvironmentConfig(new CaseInsensitiveString("test"));

        String deploymentPipeline = "deployment";

        testEnvConfig.addPipeline(new CaseInsensitiveString(deploymentPipeline));
        testEnvConfig.addAgent("agent-two");
        envsConfig.add(testEnvConfig);

        EnvironmentPipelineMatcher matcher = envsConfig.matchersForPipeline("non-existing-pipeline");

        assertNull(matcher);
    }

    @Test
    public void shouldFindEnvironmentGivenPipelineName() {
        assertThat(envsConfig.findEnvironmentForPipeline(new CaseInsensitiveString("deployment")), is(basicEnvConfig));
    }

    @Test
    public void shouldReturnNullAsEnvironmentGivenNonExistingPipelineName() {
        assertThat(envsConfig.findEnvironmentForPipeline(new CaseInsensitiveString("non-existing-pipeline")), nullValue());
    }

    @Test
    public void shouldFindIfAGivenPipelineBelongsToAnyEnvironment() {
        assertThat(envsConfig.isPipelineAssociatedWithAnyEnvironment(new CaseInsensitiveString("deployment")), is(true));
    }

    @Test
    public void shouldFindOutIfAGivenPipelineDoesNotBelongsToAnyEnvironment() {
        assertThat(envsConfig.isPipelineAssociatedWithAnyEnvironment(new CaseInsensitiveString("unit-test")), is(false));
    }

    @Test
    public void shouldFindOutIfGivenAgentUUIDIsReferencedByAnyEnvironment() {
        assertThat(envsConfig.isAgentAssociatedWithEnvironment("agent-one"), is(true));
    }

    @Test
    public void shouldFindOutIfGivenAgentUUIDIsNotReferencedByAnyEnvironment() {
        assertThat(envsConfig.isAgentAssociatedWithEnvironment("agent-not-in-any-basicEnvConfig"), is(false));
    }

    @Test
    public void shouldFindEnvironmentConfigGivenAnEnvironmentName() {
        assertThat(envsConfig.named(new CaseInsensitiveString("uat")), is(basicEnvConfig));
    }

    @Test
    public void shouldUnderstandEnvironmentsForAgent() {
        assertThat(envsConfig.environmentsForAgent("agent-one"), hasItem("uat"));
    }

    @Test
    public void shouldFindEnvironmentConfigsForAgent() {
        Set<EnvironmentConfig> environmentConfigs = envsConfig.environmentConfigsForAgent("agent-one");
        assertThat(environmentConfigs, hasItem(basicEnvConfig));
        assertThat(environmentConfigs, hasSize(1));
    }

    @Test
    public void shouldThrowExceptionIfTheEnvironmentDoesNotExist() {
        try {
            envsConfig.named(new CaseInsensitiveString("not-exist"));
            fail("Should throw exception if the environment does not exist");
        } catch (RecordNotFoundException e) {
            assertThat(e.getMessage(), is(EntityType.Environment.notFoundMessage("not-exist")));
        }
    }

    @Test
    public void shouldRemoveAgentFromAllEnvironments() {
        BasicEnvironmentConfig env2 = new BasicEnvironmentConfig(new CaseInsensitiveString("prod"));
        env2.addPipeline(new CaseInsensitiveString("test"));
        env2.addAgent("agent-one");
        env2.addAgent("agent-two");
        envsConfig.add(env2);

        BasicEnvironmentConfig env3 = new BasicEnvironmentConfig(new CaseInsensitiveString("dev"));
        env3.addPipeline(new CaseInsensitiveString("build"));
        env3.addAgent("agent-two");
        env3.addAgent("agent-three");
        envsConfig.add(env3);

        assertThat(envsConfig.get(0).getAgents().size(), is(1));
        assertThat(envsConfig.get(1).getAgents().size(), is(2));
        assertThat(envsConfig.environmentsForAgent("agent-one").size(), is(2));

        envsConfig.removeAgentFromAllEnvironments("agent-one");

        assertThat(envsConfig.get(0).getAgents().size(), is(0));
        assertThat(envsConfig.get(1).getAgents().size(), is(1));
        assertThat(envsConfig.get(2).getAgents().size(), is(2));
        assertThat(envsConfig.environmentsForAgent("agent-one").size(), is(0));
        assertThat(envsConfig.environmentsForAgent("agent-two").size(), is(2));
        assertThat(envsConfig.environmentsForAgent("agent-three").size(), is(1));
    }

    @Test
    public void shouldGetLocalPartsWhenOriginIsNull() {
        assertThat(envsConfig.getLocal().size(), is(1));
        assertThat(envsConfig.getLocal().get(0), is(basicEnvConfig));
    }

    @Test
    public void shouldGetLocalPartsWhenOriginIsFile() {
        basicEnvConfig.setOrigins(new FileConfigOrigin());
        assertThat(envsConfig.getLocal().size(), is(1));
        assertThat(envsConfig.getLocal().get(0), is(basicEnvConfig));
    }

    @Test
    public void shouldGetLocalPartsWhenOriginIsRepo() {
        basicEnvConfig.setOrigins(new RepoConfigOrigin());
        assertThat(envsConfig.getLocal().size(), is(0));
    }

    @Test
    public void shouldGetLocalPartsWhenOriginIsMixed() {
        basicEnvConfig.setOrigins(new FileConfigOrigin());

        BasicEnvironmentConfig prodLocalPart = new BasicEnvironmentConfig(new CaseInsensitiveString("PROD"));
        prodLocalPart.addAgent("1235");
        prodLocalPart.setOrigins(new FileConfigOrigin());
        BasicEnvironmentConfig prodRemotePart = new BasicEnvironmentConfig(new CaseInsensitiveString("PROD"));
        prodRemotePart.setOrigins(new RepoConfigOrigin());
        MergeEnvironmentConfig pairEnvironmentConfig = new MergeEnvironmentConfig(prodLocalPart, prodRemotePart);

        envsConfig.add(pairEnvironmentConfig);

        assertThat(envsConfig.getLocal().size(), is(2));
        assertThat(envsConfig.getLocal(), hasItem(basicEnvConfig));
        assertThat(envsConfig.getLocal(), hasItem(prodLocalPart));
    }

    @Nested
    class Validate {
        CruiseConfig config;
        ConfigSaveValidationContext validationContext;

        @BeforeEach
        void setUp() {
            config = GoConfigMother.configWithPipelines("deployment");
            config.addEnvironment("uat");
            validationContext = ConfigSaveValidationContext.forChain(config);
        }

        @Test
        void shouldValidateEnvsConfig() {
            envsConfig.validate(validationContext);

            assertTrue(envsConfig.get(0).errors().isEmpty());
        }

        @Test
        void shouldErrorOutIfTryingToAddSameEnvConfigMoreThanOnce() {
            envsConfig = new EnvironmentsConfig();

            basicEnvConfig = new BasicEnvironmentConfig(new CaseInsensitiveString("prod"));
            BasicEnvironmentConfig clone = new BasicEnvironmentConfig(new CaseInsensitiveString("prod"));

            envsConfig.add(basicEnvConfig);
            envsConfig.add(clone);
            envsConfig.validate(validationContext);

            assertTrue(envsConfig.get(0).errors().isEmpty());
            ConfigErrors configErrors = envsConfig.get(1).errors();
            assertFalse(configErrors.isEmpty());
            assertThat(configErrors.on("name"), is("Environment with name 'prod' already exists."));
        }

        @Test
        void shouldErrorOutIfTryingToAddUnknownPipeline() {
            envsConfig = new EnvironmentsConfig();

            basicEnvConfig = new BasicEnvironmentConfig(new CaseInsensitiveString("prod"));
            basicEnvConfig.addPipeline(new CaseInsensitiveString("non-existent-pipeline"));
            envsConfig.add(basicEnvConfig);

            envsConfig.validate(validationContext);

            ConfigErrors configErrors = envsConfig.get(0).errors();
            assertFalse(configErrors.isEmpty());
            assertThat(configErrors.on("pipeline"), is("Environment 'prod' refers to an unknown pipeline 'non-existent-pipeline'."));
        }

        @Test
        void shouldErrorOutIfTryingToAlreadyAssociatedPipeline() {
            envsConfig = new EnvironmentsConfig();

            basicEnvConfig = new BasicEnvironmentConfig(new CaseInsensitiveString("prod"));
            basicEnvConfig.addPipeline(new CaseInsensitiveString("deployment"));
            envsConfig.add(basicEnvConfig);

            BasicEnvironmentConfig qa = new BasicEnvironmentConfig(new CaseInsensitiveString("qa"));
            qa.addPipeline(new CaseInsensitiveString("deployment"));
            envsConfig.add(qa);

            envsConfig.validate(validationContext);

            assertTrue(envsConfig.get(0).errors().isEmpty());
            ConfigErrors configErrors = envsConfig.get(1).errors();
            assertFalse(configErrors.isEmpty());
            assertThat(configErrors.on("pipeline"), is("Associating pipeline(s) which is already part of prod environment"));
        }
    }

    @Test
    void shouldAddErrorsToTheConfig() {
        assertTrue(envsConfig.errors().isEmpty());

        envsConfig.addError("field-name", "some error message");

        ConfigErrors errors = envsConfig.errors();
        assertFalse(errors.isEmpty());
        assertThat(errors.size(), is(1));
        assertThat(errors.on("field-name"), is("some error message"));
    }

    @Test
    void shouldReturnAListOfAllEnvConfigNames() {
        List<CaseInsensitiveString> names = envsConfig.names();

        assertThat(names.size(), is(1));
        assertThat(names.get(0).toString(), is("uat"));
    }

    @Test
    void shouldReturnTrueIfContainsTheGivenEnvName() {
        assertTrue(envsConfig.hasEnvironmentNamed(new CaseInsensitiveString("uat")));
    }

    @Test
    void shouldReturnFalseIfDoesNotContainTheGivenEnvName() {
        assertFalse(envsConfig.hasEnvironmentNamed(new CaseInsensitiveString("prod")));
    }
}
