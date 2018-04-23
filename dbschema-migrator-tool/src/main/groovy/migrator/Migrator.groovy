package migrator

import groovy.transform.CompileStatic
import liquibase.exception.CommandLineParsingException
import liquibase.exception.LiquibaseException
import liquibase.integration.commandline.Main
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

import java.util.function.Predicate

class MigrationCommand {
    String username, password, url, changeLogFile, defaultsFile, properties
}

@CompileStatic
class Migrator {
    public static final int ERR_INVALID_ARGS = 1
    public static final int ERR_INVALID_LIQUIBASE_EXCEPTION = 2
    public static final int ERR_NO_COMMAND_GIVEN = 2

    // connection args
    public static final String USERNAME = 'username'
    public static final String PASSWORD = 'password'
    public static final String URL = 'url'
    public static final String CHANGE_LOG_FILE = 'changeLogFile'
    public static final String DEFAULTS_FILE = 'defaultsFile'
    // command args
    public static final String COUNT = 'count'
    public static final String TAG = 'tag'
    // other args
    public static final String PROPERTIES = 'properties'

    // command arguments
    public static final String UPDATE = 'update'
    public static final String UPDATE_SQL = 'updateSQL'
    public static final String ROLLBACK = 'rollback'
    public static final String ROLLBACK_TAG = 'rollbackTag'
    public static final String LIST = 'list'
    public static final String CLEAR_CHECK_SUMS = 'clearCheckSums'
    public static final String[] ALL_COMMAND = [UPDATE, UPDATE_SQL, ROLLBACK, ROLLBACK_TAG, LIST, CLEAR_CHECK_SUMS]

    private static Map<Option, Predicate<CommandLine>> optionPredicates = new LinkedHashMap<>()
    private static MigrationCommand migrationCommand = new MigrationCommand()
    private static List<Option> optionsOrdered

    static void main(String[] args) {

        optionPredicates.put(toOption(CHANGE_LOG_FILE, 'changelog file name eg.: db.changelog-master.xml', CHANGE_LOG_FILE, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                migrationCommand.setChangeLogFile(element.getOptionValue(CHANGE_LOG_FILE))
                return false
            }
        })
        optionPredicates.put(toOption(DEFAULTS_FILE, 'file containing default option values eg.: liquibase.properties', DEFAULTS_FILE, true), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                migrationCommand.setDefaultsFile(element.getOptionValue(DEFAULTS_FILE))
                return false
            }
        })
        optionPredicates.put(toOption(USERNAME, 'database username eg.: user', USERNAME, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                migrationCommand.setUsername(element.getOptionValue(USERNAME))
                return false
            }
        })
        optionPredicates.put(toOption(PASSWORD, 'database user password eg.: 12345678', PASSWORD, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                migrationCommand.setPassword(element.getOptionValue(PASSWORD))
                return false
            }
        })
        optionPredicates.put(toOption(URL, 'database url eg.: jdbc:driver:protocol:@server:port:database', URL, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                migrationCommand.setUrl(element.getOptionValue(Migrator.URL))
                return false
            }
        })
        optionPredicates.put(toOption(PROPERTIES, 'properties file with liquibase variables for SQL scripts', PROPERTIES, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                migrationCommand.setProperties(element.getOptionValue(PROPERTIES))
                return false
            }
        })
        optionPredicates.put(new Option(UPDATE, 'Update schema'), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                runCommand('update')
                return true
            }
        })
        optionPredicates.put(new Option(UPDATE_SQL, 'Generate update schema SQL script'), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                runCommand('updateSQL')
                return true
            }
        })
        optionPredicates.put(toOption(ROLLBACK, 'rollback <count> changes', COUNT, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                runCommand('rollbackCount', element.getOptionValue('rollback'))
                return true
            }
        })
        optionPredicates.put(toOption(ROLLBACK_TAG, 'rollbackTag <tag> changes', TAG, false), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                runCommand('rollback', element.getOptionValue('rollbackTag'))
                return true
            }
        })
        optionPredicates.put(new Option(LIST, 'Writes description of differences'), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                runCommand('diff')
                return true
            }
        })
        optionPredicates.put(new Option(CLEAR_CHECK_SUMS, 'Clear current changes checksums'), new Predicate<CommandLine>() {
            boolean test(CommandLine element) {
                runCommand('clearCheckSums')
                return true
            }
        })

        optionsOrdered = optionPredicates.keySet().toList()
        CommandLineParser parser = new DefaultParser()

        Options options = toOptions()
        try {
            final CommandLine commandLine = parser.parse(options, args)

            Option first = optionPredicates.keySet()
                    .find { element ->
                Predicate<CommandLine> optionPredicate = optionPredicates.get(element)
                return commandLine.hasOption(element.getOpt()) && optionPredicate.test(commandLine)
            }
            if (first == null) {
                showHelpAndExit(options, "Specify at least one command: ${(ALL_COMMAND).collect { "-$it" }.toList()}", ERR_NO_COMMAND_GIVEN)
            }
        } catch (ParseException e) {
            showHelpAndExit(options, e.getMessage(), ERR_INVALID_ARGS)
        }
    }

    private static void showHelpAndExit(Options options, String message, int status) {
        printHelp(options)
        System.err.println()
        System.err.println()
        System.err.println(message)
        System.exit(status)
    }

    private static Option toOption(String opt, String description, String argName, boolean required) {
        Option option = new Option(opt, true, description)
        option.setArgName(argName)
        option.setRequired(required)
        option
    }

    private static void runCommand(String... commandArgs) {
        try {
            System.setProperty('liquibase.scan.packages', 'liquibase.change,' +
                    'liquibase.changelog,' +
                    'liquibase.database,' +
                    'liquibase.parser,' +
                    'liquibase.precondition,' +
                    'liquibase.datatype,' +
                    'liquibase.serializer,' +
                    'liquibase.sqlgenerator,' +
                    'liquibase.executor,' +
                    'liquibase.snapshot,' +
                    'liquibase.logging,' +
                    'liquibase.diff,' +
                    'liquibase.structure,' +
                    'liquibase.structurecompare,' +
                    'liquibase.lockservice,' +
                    'liquibase.sdk.database,' +
                    'liquibase.ext')
            Main.run(getLiquibaseArgs(commandArgs))
        } catch (CommandLineParsingException | IOException | LiquibaseException e) {
            System.err.println(e.getMessage())
            System.exit(ERR_INVALID_LIQUIBASE_EXCEPTION)
        }
    }

    private static String[] getLiquibaseArgs(String... commandArgs) {
        List<String> strings = ['--changeLogFile=' + migrationCommand.changeLogFile, '--defaultsFile=' + migrationCommand.defaultsFile]
        (strings.toList() + commandArgs.toList() + loadProperties()).collect() as String[]
    }

    private static List<String> loadProperties() {
        if (migrationCommand.getProperties() != null) {
            final Properties properties = new Properties()
            properties.load(new FileInputStream(migrationCommand.getProperties()))
            Set<Object> objects = properties.keySet()
            return objects.collect { "-D$it=${properties.get(it)}".toString() }
        } else {
            return Collections.emptyList()
        }
    }

    private static Options toOptions() {
        optionPredicates.keySet().inject(new Options(), { Options acc, Option it -> acc.addOption(it) }) as Options
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter()
        formatter.setWidth(240)
        formatter.setOptionComparator(new Comparator<Option>() {
            int compare(Option o1, Option o2) {
                return optionsOrdered.indexOf(o1) - optionsOrdered.indexOf(o2)
            }
        })
        formatter.printHelp(String.format("java -jar %s", getJarName()), options, true)
    }

    private static String getJarName() {
        String full = Migrator.class.getProtectionDomain().getCodeSource().getLocation().toString()
        int beginIndex = full.lastIndexOf('/')
        return beginIndex > -1 ? full.substring(beginIndex + 1) : full
    }

}