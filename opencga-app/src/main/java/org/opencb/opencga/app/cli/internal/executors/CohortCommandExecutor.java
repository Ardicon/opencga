package org.opencb.opencga.app.cli.internal.executors;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.analysis.cohort.CohortIndexTask;
import org.opencb.opencga.app.cli.internal.options.CohortCommandOptions;
import org.opencb.opencga.core.exceptions.ToolException;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CohortCommandExecutor extends InternalCommandExecutor {

    private final CohortCommandOptions cohortCommandOptions;

    public CohortCommandExecutor(CohortCommandOptions options) {
        super(options.cohortCommandOptions);
        cohortCommandOptions = options;
    }

    @Override
    public void execute() throws Exception {
        logger.debug("Executing cohort command line");

        String subCommandString = getParsedSubCommand(cohortCommandOptions.jCommander);
        configure();
        switch (subCommandString) {
            case "secondary-index":
                secondaryIndex();
                break;
            default:
                logger.error("Subcommand not valid");
                break;

        }
    }

    private void secondaryIndex() throws ToolException {
        CohortCommandOptions.SecondaryIndex options = cohortCommandOptions.secondaryIndex;

        Path outDir = Paths.get(options.outDir);
        Path opencgaHome = Paths.get(configuration.getWorkspace()).getParent();

        // Prepare analysis parameters and config
        CohortIndexTask indexTask = new CohortIndexTask();
        indexTask.setUp(opencgaHome.toString(), new ObjectMap(), outDir, options.commonOptions.token);
        indexTask.start();
    }

}
