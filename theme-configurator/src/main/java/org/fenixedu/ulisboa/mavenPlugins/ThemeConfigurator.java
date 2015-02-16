package org.fenixedu.ulisboa.mavenPlugins;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Mojo(name = "configure-theme", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class ThemeConfigurator extends AbstractMojo {

    @Parameter(property = "project")
    protected MavenProject mavenProject;

    @Parameter(property = "theme-artifact-name", defaultValue = "fenixedu-ulisboa-theme")
    protected String dependedThemeArtifactName;

    @Parameter(property = "theme-name", defaultValue = "ulisboa")
    protected String dependedThemeName;

    @Parameter(property = "theme-name", defaultValue = "branding.less")
    protected String brandingSpecificationFileName;

    @Parameter(property = "less-maven-plugin-version", defaultValue = "1.7.0.1.1")
    protected String lessPluginVersion;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (mavenProject.getPackaging().equals("pom")) {
            getLog().info("Project is pom type. Skipping less generation");
            return;
        }

        File brandingSpecification =
                new File(mavenProject.getBasedir() + File.separator + "src" + File.separator + "main" + File.separator + "less"
                        + File.separator + brandingSpecificationFileName);

        if (!brandingSpecification.exists()) {
            getLog().info("This project has no branding specification. Using default");
            return;
        }

        @SuppressWarnings("unchecked")
        Set<Artifact> dependencyArtifacts = mavenProject.getDependencyArtifacts();

        Optional<Artifact> findAny =
                dependencyArtifacts.stream().filter(x -> x.getArtifactId().equals(dependedThemeArtifactName)).findAny();

        if (!findAny.isPresent()) {
            getLog().warn("Theme " + dependedThemeArtifactName + " not found. Application will be compiled without new styles");
            return;
        }

        try (JarFile jarFile = new JarFile(findAny.get().getFile())) {
            if (!recompilationIsRequired(findAny.get().getFile(), brandingSpecification)) {
                return;
            }

            copyLessContents(jarFile);
            copyStaticResources(jarFile);

            String extractedLessDirectory =
                    mavenProject.getBuild().getDirectory() + File.separator + "less" + File.separator + "themes" + File.separator
                            + dependedThemeName + File.separator + "less" + File.separator;

            copyBrandingSpecification(brandingSpecification, extractedLessDirectory);

            compileLess(extractedLessDirectory);

            createLogFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean recompilationIsRequired(File jarFile, File brandingSpecification) throws IOException {
        File logFile = getLogFile();
        if (!logFile.exists()) {
            return true;
        }
        FileTime lastModifiedTimeBrandingSpecefication =
                Files.getLastModifiedTime(Paths.get(brandingSpecification.getAbsolutePath()));
        FileTime lastModifiedTimeJarFile = Files.getLastModifiedTime(Paths.get(jarFile.getAbsolutePath()));

        FileTime lastModifiedTimeLogFile = Files.getLastModifiedTime(Paths.get(logFile.getAbsolutePath()));

        if (lastModifiedTimeBrandingSpecefication.compareTo(lastModifiedTimeLogFile) > 0
                || lastModifiedTimeJarFile.compareTo(lastModifiedTimeLogFile) > 0) {
            //There's a small chance this may fail if we update the dependencies of the project to a new theme version that was already loaded
            return true;
        }
        getLog().info("Skipping Less compilation.");
        return false;
    }

    private File getLogFile() {
        String logFileLocation =
                mavenProject.getBuild().getDirectory() + File.separator + "less" + File.separator + ".lessGenerationLog";
        return new File(logFileLocation);
    }

    private void copyBrandingSpecification(File brandingSpecification, String extractedLessDirectory) throws IOException {
        InputStream is = new FileInputStream(brandingSpecification);
        FileOutputStream fos = new FileOutputStream(extractedLessDirectory + brandingSpecificationFileName);
        while (is.available() > 0) {
            fos.write(is.read());
        }
        fos.close();
        is.close();

    }

    public void copyLessContents(JarFile jar) throws IOException {
        File folder = new File(mavenProject.getBuild().getDirectory() + File.separator + "less");
        folder.mkdirs();

        Enumeration<JarEntry> enumEntries = jar.entries();
        while (enumEntries.hasMoreElements()) {
            JarEntry file = enumEntries.nextElement();
            //Only copy the less files
            if (!file.getName().startsWith("less")) {
                continue;
            }

            File f = new File(mavenProject.getBuild().getDirectory() + File.separator + file.getName());

            if (file.isDirectory()) {
                f.mkdir();
                continue;
            }

            FileOutputStream fos = new FileOutputStream(f);
            InputStream is = jar.getInputStream(file);
            while (is.available() > 0) {
                fos.write(is.read());
            }
            is.close();
            fos.close();
        }
    }

    //TODO Almost copy pasted from copyLessContents. Refactor
    public void copyStaticResources(JarFile jar) throws IOException {
        File folder = new File(mavenProject.getBuild().getDirectory() + File.separator + "classes/META-INF/resources/themes/");
        folder.mkdirs();

        Enumeration<JarEntry> enumEntries = jar.entries();
        while (enumEntries.hasMoreElements()) {
            JarEntry file = enumEntries.nextElement();
            //Only copy static resources of the theme
            if (!file.getName().startsWith("META-INF/resources/themes/") || file.getName().endsWith("style.css")) {
                continue;
            }
            String pathname =
                    mavenProject.getBuild().getDirectory() + File.separator + "classes" + File.separator
                            + file.getName().replaceAll("ulisboa", mavenProject.getArtifactId());
            File f = new File(pathname);

            if (file.isDirectory()) {
                f.mkdir();
                continue;
            }

            FileOutputStream fos = new FileOutputStream(f);
            if (file.getName().endsWith("html")) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(jar.getInputStream(file)));
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    if (file.getName().endsWith("html")) {
                        fos.write(line.replaceAll("ulisboa", mavenProject.getArtifactId() + '\n').getBytes());
                    }
                }
                bufferedReader.close();
            } else {
                InputStream is = jar.getInputStream(file);
                while (is.available() > 0) {
                    fos.write(is.read());
                }
                is.close();
            }
            fos.close();
        }
    }

    private void compileLess(String sourceDirectory) throws MojoExecutionException {
        Plugin plugin = plugin(groupId("org.lesscss"), artifactId("lesscss-maven-plugin"), version(lessPluginVersion));
        Xpp3Dom configuration =
                configuration(
                        element(name("sourceDirectory"), sourceDirectory),
                        element(name("outputDirectory"), "${project.basedir}/target/classes/META-INF/resources/themes/"
                                + mavenProject.getArtifactId() + "/css"), element(name("compress"), "true"),
                        element(name("includes"), element(name("include"), "style.less")));
        executeMojo(plugin, goal("compile"), configuration, executionEnvironment(mavenProject, mavenSession, pluginManager));
    }

    private void createLogFile() throws IOException {
        getLogFile().createNewFile();
    }
}
