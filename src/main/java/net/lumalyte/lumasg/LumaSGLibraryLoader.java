package net.lumalyte.lumasg;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.graph.Dependency;
import org.jetbrains.annotations.NotNull;

public class LumaSGLibraryLoader implements PluginLoader {
    
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        // Create resolver for Maven repositories
        var resolver = new io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver();
        
        // Add required repositories
        resolver.addRepository(new RemoteRepository.Builder("xenondevs", "default", "https://repo.xenondevs.xyz/releases/").build());
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());
        
        // Core dependencies (previously compileOnly, now runtime loaded)
        resolver.addDependency(new Dependency(new DefaultArtifact("xyz.xenondevs.invui:invui:pom:1.46"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.squareup.okhttp3:okhttp:4.12.0"), null));
        
        // Caching (previously shadowed)
        resolver.addDependency(new Dependency(new DefaultArtifact("com.github.ben-manes.caffeine:caffeine:3.2.1"), null));
        
        // Database Connection Pooling (previously shadowed)
        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:5.0.1"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.postgresql:postgresql:42.6.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("mysql:mysql-connector-java:8.0.33"), null));
        
        // High-Performance Serialization (previously shadowed)
        resolver.addDependency(new Dependency(new DefaultArtifact("com.esotericsoftware:kryo:5.6.2"), null));
        
        // Input Validation & Security (previously shadowed)
        resolver.addDependency(new Dependency(new DefaultArtifact("org.hibernate.validator:hibernate-validator:9.0.1.Final"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.apache.commons:commons-text:1.10.0"), null));
        
        // Discord Integration (JDA 5.6.1)
        resolver.addDependency(new Dependency(new DefaultArtifact("net.dv8tion:JDA:5.6.1"), null));
        
        // Add the resolver to the classpath
        classpathBuilder.addLibrary(resolver);
    }
} 
