package org.carlspring.strongbox.config;

import org.carlspring.strongbox.booters.PropertiesBooter;
import org.carlspring.strongbox.data.server.EmbeddedOrientDbServer;
import org.carlspring.strongbox.data.server.OrientDbServer;
import org.carlspring.strongbox.util.FileSystemUtils;
import org.carlspring.strongbox.util.JarFileUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.orientechnologies.orient.core.db.OrientDB;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author Przemyslaw Fusik
 */
@Configuration
@Conditional(EmbeddedOrientDbConfig.class)
class EmbeddedOrientDbConfig
        extends CommonOrientDbConfig
        implements Condition
{

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedOrientDbConfig.class);

    @Inject
    private PropertiesBooter propertiesBooter;

    @Value("${strongbox.database.snapshot.resource:classpath:/db-import}")
    private Resource databaseSnapshotResource;


    @Bean(destroyMethod = "close")
    @DependsOn("orientDbServer")
    OrientDB orientDB()
            throws IOException, URISyntaxException
    {
        String url = StringUtils.substringBeforeLast(connectionConfig.getUrl(), "/");
        OrientDB orientDB = new OrientDB(url,
                                         connectionConfig.getUsername(),
                                         connectionConfig.getPassword(),
                                         getOrientDBConfig());
        String database = connectionConfig.getDatabase();

        if (!orientDB.exists(database))
        {
            logger.info(String.format("Database does not exist. Copying fresh database snapshot from [%s]...",
                                      databaseSnapshotResource));

            Path snapshotPath;
            try
            {
                snapshotPath = Paths.get(databaseSnapshotResource.getURI());
            }
            catch (FileSystemNotFoundException ex)
            {
                snapshotPath = JarFileUtils.resolvePathInJar(databaseSnapshotResource.getURI().toString());
            }

            FileSystemUtils.copyRecursively(snapshotPath, Paths.get(propertiesBooter.getVaultDirectory() + "/db"));
        }
        else
        {
            logger.info("Re-using existing database " + database + ".");
        }
        return orientDB;
    }

    @Bean
    OrientDbServer orientDbServer()
    {
        return new EmbeddedOrientDbServer();
    }

    @Override
    public boolean matches(ConditionContext conditionContext,
                           AnnotatedTypeMetadata metadata)

    {
        return ConnectionConfigOrientDB.resolveProfile(conditionContext.getEnvironment())
                                       .equals(ConnectionConfigOrientDB.PROFILE_EMBEDDED);
    }
}
