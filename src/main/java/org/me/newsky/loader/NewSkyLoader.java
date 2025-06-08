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

        resolveLibraries().forEach(lib -> resolver.addDependency(new Dependency(new DefaultArtifact(lib), null)));

        resolver.addRepository(new RemoteRepository.Builder("maven", "default", "https://repo.maven.apache.org/maven2/").build());

        classpathBuilder.addLibrary(resolver);
    }

    @NotNull
    private List<String> resolveLibraries() {
        try (InputStream input = getLibraryListFile()) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(Objects.requireNonNull(input, "Failed to read paper-libraries.yml"));

            Object libs = map.get("libraries");
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
