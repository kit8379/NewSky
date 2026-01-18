package org.me.newsky.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class NewSkyLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        final MavenLibraryResolver resolver = new MavenLibraryResolver();

        // Add dependencies from paper-libraries.yml
        for (String lib : resolveLibraries()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(lib), null));
        }

        resolver.addRepository(new RemoteRepository.Builder("maven-central-mirror", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        resolver.addRepository(new RemoteRepository.Builder("papermc", "default", "https://repo.papermc.io/repository/maven-public/").build());

        resolver.addRepository(new RemoteRepository.Builder("sonatype", "default", "https://oss.sonatype.org/content/groups/public/").build());

        resolver.addRepository(new RemoteRepository.Builder("is-releases", "default", "https://repo.rapture.pw/repository/maven-releases/").build());

        resolver.addRepository(new RemoteRepository.Builder("is-snapshots", "default", "https://repo.infernalsuite.com/repository/maven-snapshots/").build());

        resolver.addRepository(new RemoteRepository.Builder("placeholderapi", "default", "https://repo.extendedclip.com/content/repositories/placeholderapi/").build());

        classpathBuilder.addLibrary(resolver);
    }

    @NotNull
    private List<String> resolveLibraries() {
        try (InputStream input = getLibraryListFile()) {
            final Yaml yaml = new Yaml();
            final Map<String, Object> map = yaml.load(Objects.requireNonNull(input, "Failed to read paper-libraries.yml"));

            final Object libs = map.get("libraries");
            if (!(libs instanceof List<?> list)) {
                throw new IllegalStateException("'libraries' must be a list");
            }

            return list.stream().map(Object::toString).toList();
        } catch (Exception e) {
            throw new RuntimeException("Unable to load libraries from paper-libraries.yml", e);
        }
    }

    private InputStream getLibraryListFile() {
        return NewSkyLoader.class.getClassLoader().getResourceAsStream("paper-libraries.yml");
    }
}
