package com.me.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the default config folder.
 *
 * Order: the "consolecompact.config.dir" system property, then a "maps" folder next to the
 * working directory (a repository checkout - handy for development and the simulator),
 * then the Bitwig user directory.
 */
public final class DefaultConfigDir
{
   public static final String PROPERTY = "consolecompact.config.dir";
   public static final String USER_DIR_NAME = "BitwigCompact";

   private DefaultConfigDir()
   {
   }

   public static Path resolve()
   {
      final String override = System.getProperty(PROPERTY);
      if (override != null && !override.isBlank())
      {
         return Paths.get(override);
      }
      final Path localMaps = Paths.get("maps");
      if (Files.isDirectory(localMaps))
      {
         return localMaps;
      }
      return bitwigUserDir();
   }

   /** The per-user config folder inside the Bitwig user directory. */
   public static Path bitwigUserDir()
   {
      return Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", USER_DIR_NAME);
   }
}
