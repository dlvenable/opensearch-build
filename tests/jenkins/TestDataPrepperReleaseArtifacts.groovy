/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import jenkins.tests.BuildPipelineTest
import org.junit.Before
import org.junit.Test

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat

class TestDataPrepperReleaseArtifacts extends BuildPipelineTest {

    private String version

    @Before
    void setUp() {
        this.registerLibTester(new SignArtifactsLibTester( '.sig', 'linux',  'archive', null, null))

        super.setUp()

        version = '0.22.1'

        binding.setVariable('VERSION', version)
        binding.setVariable('DATA_PREPPER_BUILD_NUMBER', '997908')

        binding.setVariable('DATA_PREPPER_ARTIFACT_STAGING_SITE', 'http://data-prepper-staging-artifacts')

        binding.setVariable('ARTIFACT_PRODUCTION_BUCKET_NAME', 'production-s3-bucket-name')
        binding.setVariable('ARTIFACT_PROMOTION_ROLE_NAME', 'production-role-name')
        binding.setVariable('AWS_ACCOUNT_ARTIFACT', 'aws-account-artifact')

        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })

        helper.registerAllowedMethod('s3Upload', [Map], {})
    }

    @Test
    void 'release-data-prepper-all-artifacts builds consistently with same inputs'() {
        testPipeline('jenkins/data-prepper/release-data-prepper-all-artifacts.jenkinsfile',
                'tests/jenkins/jobs/data-prepper/release-data-prepper-all-artifacts.jenkinsfile')
    }

    @Test
    void 'downloads from the correct URLs'() {
        runScript('jenkins/data-prepper/release-data-prepper-all-artifacts.jenkinsfile')

        def curlCommands = helper.callStack.findAll { call ->
            call.methodName == 'sh'
        }.collect { call ->
            callArgsToString(call)
        }.findAll { shCommand ->
            shCommand.contains('curl')
        }

        assertThat(curlCommands, hasItem(
                "curl -sSL http://data-prepper-staging-artifacts/${version}/997908/archive/opensearch-data-prepper-${version}-linux-x64.tar.gz -o archive/opensearch-data-prepper-${version}-linux-x64.tar.gz".toString()
        ))
        assertThat(curlCommands, hasItem(
                "curl -sSL http://data-prepper-staging-artifacts/${version}/997908/archive/opensearch-data-prepper-jdk-${version}-linux-x64.tar.gz -o archive/opensearch-data-prepper-jdk-${version}-linux-x64.tar.gz".toString()
        ))
    }

    @Test
    void 's3Upload uploads to the correct path'() {
        runScript('jenkins/data-prepper/release-data-prepper-all-artifacts.jenkinsfile')

        def actualS3Upload = helper.callStack.findAll { call ->
            call.methodName == 's3Upload'
        }.first()

        assertThat(actualS3Upload, notNullValue())
        assertThat(actualS3Upload.args[0].get('path'), equalTo("data-prepper/${version}/"))
    }
}
