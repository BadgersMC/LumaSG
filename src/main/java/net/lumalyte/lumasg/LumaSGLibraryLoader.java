package net.lumalyte;

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
        
        // Add InvUI dependency
        resolver.addDependency(new Dependency(new DefaultArtifact("xyz.xenondevs.invui:invui:pom:1.46"), null));
        
        // Add OkHttp dependency
        resolver.addDependency(new Dependency(new DefaultArtifact("com.squareup.okhttp3:okhttp:4.12.0"), null));
        
        // Add the resolver to the classpath
        classpathBuilder.addLibrary(resolver);
    }
} 