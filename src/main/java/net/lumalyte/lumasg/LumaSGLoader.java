package net.lumalyte.lumasg;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public class LumaSGLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addRepository(new RemoteRepository.Builder(
                "xenondevs", "default", "https://repo.xenondevs.xyz/releases/").build());

        // Kotlin runtime
        resolver.addDependency(dep("org.jetbrains.kotlin:kotlin-stdlib:2.1.0"));
        resolver.addDependency(dep("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0"));

        // Database
        resolver.addDependency(dep("org.jetbrains.exposed:exposed-core:0.55.0"));
        resolver.addDependency(dep("org.jetbrains.exposed:exposed-dao:0.55.0"));
        resolver.addDependency(dep("org.jetbrains.exposed:exposed-jdbc:0.55.0"));
        resolver.addDependency(dep("org.jetbrains.exposed:exposed-java-time:0.55.0"));
        resolver.addDependency(dep("com.zaxxer:HikariCP:5.1.0"));
        resolver.addDependency(dep("org.mariadb.jdbc:mariadb-java-client:3.3.3"));
        resolver.addDependency(dep("org.xerial:sqlite-jdbc:3.45.3.0"));

        // GUI — invui is a POM-only BOM; use "pom" extension so Aether resolves transitively
        resolver.addDependency(new Dependency(new DefaultArtifact("xyz.xenondevs.invui:invui:pom:1.49"), null));

        // Discord
        resolver.addDependency(dep("net.dv8tion:JDA:5.6.1"));

        classpathBuilder.addLibrary(resolver);
    }

    private static Dependency dep(String coords) {
        return new Dependency(new DefaultArtifact(coords), null);
    }
}
