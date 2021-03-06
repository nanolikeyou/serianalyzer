/**
 *   This file is part of Serianalyzer.
 *
 *   Serianalyzer is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Serianalyzer is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Serianalyzer.  If not, see <http://www.gnu.org/licenses/>.
 *   
 * Copyright 2015,2016 Moritz Bechler <mbechler@eenterphace.org>
 * 
 * Created: 11.11.2015 by mbechler
 */
package serianalyzer;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;


/**
 * @author mbechler
 *
 */
public class Main {

    private static final Logger log = Logger.getLogger(Main.class);


    /**
     * @param args
     * @throws InterruptedException
     */
    public static void main ( String[] args ) throws InterruptedException {
        long start = System.currentTimeMillis();

        List<String> remainArgs = new ArrayList<>();
        SerianalyzerConfig config = configure(args, remainArgs);
        final SerianalyzerInput input = new SerianalyzerInput(config);

        if ( remainArgs.isEmpty() ) {
            System.err.println("Usage: serianalyzer [-v] [-w|--whitelist <whitelist>] [-i|-o <file>] [-t <type>] [-n] [-d] <jar file/directory...>"); //$NON-NLS-1$
            System.err.println("    -w      Whitelist file"); //$NON-NLS-1$
            System.err.println("    -n      Disable heuristics"); //$NON-NLS-1$
            System.err.println("    -d      Dump instantiation details"); //$NON-NLS-1$
            System.err.println("    -i      Read analyzed state (do not run on untrusted inputs ;))"); //$NON-NLS-1$
            System.err.println("    -o      Write analyzed state (do not run on untrusted inputs ;))"); //$NON-NLS-1$
            System.err.println("    -v      Print out verbose output, including progress and timing info (will also be logged regardless)"); //$NON-NLS-1$
            System.err.println("    -t      Use custom initial method set (multiple possible) " + Arrays.toString(InitialSetType.values())); //$NON-NLS-1$
            System.err.println("    -a      Include non-serializable types in analysis"); //$NON-NLS-1$
            System.err.println("    -j      Don't include default java serialization methods in analysis"); //$NON-NLS-1$
            System.err.println();
            System.exit(-1);
        }

        try {
            for ( String arg : remainArgs ) {
                log.info("Indexing " + arg); //$NON-NLS-1$
                input.index(Paths.get(arg));
            }
        }
        catch ( IOException e ) {
            log.error(e);
        }

        log.info("Indexing complete"); //$NON-NLS-1$

        boolean res = false;
        Serianalyzer analyzer = new Serianalyzer(input);
        try {
            res = analyzer.analyze();
        }
        catch ( SerianalyzerException e ) {
            e.printStackTrace();
            log.error("Failed to perform analysis", e); //$NON-NLS-1$
        }

        log.info("Completed, took " + ( System.currentTimeMillis() - start ) / 1000 + " s"); //$NON-NLS-1$ //$NON-NLS-2$

        if ( res ) {
            log.warn("Found non whitelisted instances"); //$NON-NLS-1$
            System.exit(-1);
        }
    }


    /**
     * @param args
     * @param remainArgs
     * @return
     */
    private static SerianalyzerConfig configure ( String[] args, List<String> remainArgs ) {

        List<String> whitelistArgs = new ArrayList<>();
        boolean noHeuristics = false;
        boolean dumpInstantiation = false;
        boolean includeNonSerializable = false;
        boolean excludeJavaSerialization = false;
        File saveFile = null;
        File restoreFile = null;
        Set<InitialSetType> initialSet = new HashSet<>();

        int i = 0;
        for ( ; i < args.length; i++ ) {
            String arg = args[ i ];
            if ( "-w".equals(arg) || "--whitelist".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                whitelistArgs.addAll(Arrays.asList(args[ ++i ].split(","))); //$NON-NLS-1$
            }
            else if ( "-n".equals(arg) || "--noheuristic".equals(arg) ) { //$NON-NLS-1$//$NON-NLS-2$
                noHeuristics = true;
            }
            else if ( "-d".equals(arg) || "--dumpinstantiation".equals(arg) ) { //$NON-NLS-1$//$NON-NLS-2$
                dumpInstantiation = true;
            }
            else if ( "-i".equals(arg) || "--input".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                i++;
                restoreFile = new File(args[ i ]);
            }
            else if ( "-o".equals(arg) || "--output".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                i++;
                saveFile = new File(args[ i ]);
            }
            else if ( "-v".equals(arg) || "--verbose".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                Verbose.VERBOSE = true;
            }
            else if ( "-t".equals(arg) || "--initialSet".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                i++;
                initialSet.add(InitialSetType.valueOf(args[ i ]));
            }
            else if ( "-a".equals(arg) || "--allTypes".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                includeNonSerializable = true;
            }
            else if ( "-j".equals(arg) || "--noJavaDeserializtion".equals(arg) ) { //$NON-NLS-1$ //$NON-NLS-2$
                excludeJavaSerialization = true;
            }
            else {
                break;
            }
        }

        if ( initialSet.isEmpty() ) {
            initialSet.add(InitialSetType.JAVA);
        }

        SerianalyzerConfig config = new SerianalyzerConfig(noHeuristics, dumpInstantiation);
        config.setInitialSet(initialSet);
        for ( String whitelistArg : whitelistArgs ) {
            log.info("Loading whitelist " + whitelistArg); //$NON-NLS-1$
            try ( FileInputStream fis = new FileInputStream(whitelistArg) ) {
                config.readFile(fis);
            }
            catch ( IOException e ) {
                log.error("Failed to load whitelist", e); //$NON-NLS-1$
                System.exit(-1);
            }
        }

        config.setSaveTo(saveFile);
        config.setRestoreFrom(restoreFile);
        config.setCheckJavaSerialization(!excludeJavaSerialization);
        config.setCheckNonSerializable(includeNonSerializable);

        for ( ; i < args.length; i++ ) {
            remainArgs.add(args[ i ]);
        }
        return config;
    }
}
