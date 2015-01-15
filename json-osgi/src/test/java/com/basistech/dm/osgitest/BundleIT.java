/******************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp.  It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2014 Basis Technology Corporation All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ******************************************************************************/

package com.basistech.dm.osgitest;

import com.basistech.rosette.dm.jackson.AnnotatedDataModelModule;
import com.basistech.rosette.dm.LanguageDetection;
import com.basistech.rosette.dm.Name;
import com.basistech.util.ISO15924;
import com.basistech.util.LanguageCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemPackages;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * IT to show that the OSGi bundle works somewhat.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BundleIT {

    private ObjectMapper objectMapper() {
        return AnnotatedDataModelModule.setupObjectMapper(new ObjectMapper());
    }

    private String getDependencyVersion(String groupId, String artifactId) {
        URL depPropsUrl = Resources.getResource("META-INF/maven/dependencies.properties");
        Properties depProps = new Properties();
        try {
            depProps.load(Resources.asByteSource(depPropsUrl).openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return (String)depProps.get(String.format("%s/%s/version", groupId, artifactId));
    }

    @Configuration
    public Option[] config() {
        String projectBuildDirectory = System.getProperty("project.build.directory");
        String projectVersion = System.getProperty("project.version");

        List<String> bundleUrls = Lists.newArrayList();
        File bundleDir = new File(projectBuildDirectory, "bundles");
        File[] bundleFiles = bundleDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        for (File bundleFile : bundleFiles) {
            try {
                bundleUrls.add(bundleFile.toURI().toURL().toExternalForm());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        bundleUrls.add(String.format("file:%s/adm-json-osgi-%s.jar", projectBuildDirectory, projectVersion));

        String[] bundles = bundleUrls.toArray(new String[bundleUrls.size()]);
        return options(
                provision(bundles),
                systemPackages(
                        // These are needed for guava.
                        "sun.misc",
                        "javax.annotation",
                        String.format("org.slf4j;version=\"%s\"", getDependencyVersion("org.slf4j", "slf4j-api"))

                ),
                junitBundles(),
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("WARN")
        );
    }

    @Test
    public void modelWorksAtAll() {
        // the code in here will blow up if we can't talk to 'LanguageCode' which comes from another bundle.
        LanguageDetection.DetectionResult.Builder builder = new LanguageDetection.DetectionResult.Builder(LanguageCode.FINNISH);
        builder.build();
    }

    @Test
    public void jsonWorksABit() throws Exception {
        List<Name> names = Lists.newArrayList();
        Name.Builder builder = new Name.Builder("Fred");
        names.add(builder.build());
        builder = new Name.Builder("George");
        builder.languageOfOrigin(LanguageCode.ENGLISH).script(ISO15924.Latn).languageOfUse(LanguageCode.FRENCH);
        names.add(builder.build());
        ObjectMapper mapper = objectMapper();
        String json = mapper.writeValueAsString(names);
        // one way to inspect the works is to read it back in _without_ our customized mapper.
        ObjectMapper plainMapper = new ObjectMapper();
        JsonNode tree = plainMapper.readTree(json);
        Assert.assertTrue(tree.isArray());
        Assert.assertEquals(2, tree.size());
        JsonNode node = tree.get(0);
        Assert.assertTrue(node.has("text"));
        Assert.assertEquals("Fred", node.get("text").asText());
        Assert.assertFalse(node.has("script"));
        Assert.assertFalse(node.has("languageOfOrigin"));
        Assert.assertFalse(node.has("languageOfUse"));

        List<Name> readBack = mapper.readValue(json, new TypeReference<List<Name>>(){ });
        Assert.assertEquals(names, readBack);
    }
}