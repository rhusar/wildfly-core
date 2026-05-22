/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.readline.Prompt;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersConverter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.installationmanager.ArtifactChange;

@CommandDefinition(name = "update", description = "Apply the latest available patches on a server instance.", activator = InstMgrActivator.class)
public class UpdateCommand extends AbstractInstMgrCommand {
    public static final String DRY_RUN_OPTION = "dry-run";
    public static final String CONFIRM_OPTION = "confirm";
    @Option(name = DRY_RUN_OPTION, hasValue = false, activator = AbstractInstMgrCommand.DryRunActivator.class)
    private boolean dryRun;
    @Option(name = CONFIRM_OPTION, hasValue = false, activator = AbstractInstMgrCommand.ConfirmActivator.class)
    private boolean confirm;
    @OptionList(name = "repositories")
    private List<String> repositories;
    @Option(name = "local-cache")
    private File localCache;
    @Option(name = NO_RESOLVE_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.NoResolveLocalCacheActivator.class, defaultValue = "true")
    private boolean noResolveLocalCache;
    @Option(name = USE_DEFAULT_LOCAL_CACHE_OPTION, hasValue = false, activator = AbstractInstMgrCommand.UseDefaultLocalCacheActivator.class)
    private boolean useDefaultLocalCache;
    @Option(name = "offline", hasValue = false)
    private boolean offline;
    @OptionList(name = "maven-repo-files")
    private List<File> mavenRepoFiles;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class)
    public ModelNode headers;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        if (confirm && dryRun) {
            throw new CommandException(String.format("%s and %s cannot be used at the same time.", CONFIRM_OPTION, DRY_RUN_OPTION));
        }

        ParsedCommandLine cmdParser = ctx.getParsedCommandLine();
        final Boolean optNoResolveLocalCache = cmdParser.hasProperty("--" + NO_RESOLVE_LOCAL_CACHE_OPTION) ? noResolveLocalCache : null;
        final Boolean optUseDefaultLocalCache = cmdParser.hasProperty("--" + USE_DEFAULT_LOCAL_CACHE_OPTION) ? useDefaultLocalCache : null;

        ListUpdatesAction.Builder listUpdatesCmdBuilder = new ListUpdatesAction.Builder()
                .setNoResolveLocalCache(optNoResolveLocalCache)
                .setUseDefaultLocalCache(optUseDefaultLocalCache)
                .setLocalCache(localCache)
                .setRepositories(repositories)
                .setMavenRepoFiles(mavenRepoFiles)
                .setOffline(offline)
                .setHeaders(headers);

        ListUpdatesAction listUpdatesCmd = listUpdatesCmdBuilder.build();
        ModelNode response = listUpdatesCmd.executeOp(ctx, this.host);

        if (response.hasDefined(Util.RESULT)) {
            final ModelNode result = response.get(Util.RESULT);
            final List<ModelNode> changesMn = result.get(InstMgrConstants.LIST_UPDATES_RESULT).asListOrEmpty();
            printListUpdatesResult(commandInvocation, changesMn);

            if (dryRun) {
                return CommandResult.SUCCESS;
            }

            Path lstUpdatesWorkDir = null;
            if (result.hasDefined(InstMgrConstants.LIST_UPDATES_WORK_DIR)) {
                lstUpdatesWorkDir = Paths.get(result.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).asString());
            }

            if (!changesMn.isEmpty()) {
                if (!confirm) {
                    String reply = null;

                    try {
                        while (reply == null) {
                            reply = commandInvocation.inputLine(new Prompt("\nWould you like to proceed with preparing this update? [y/N]:"));
                            if (reply != null && reply.equalsIgnoreCase("N")) {
                                // clean the cache if there is one
                                if (lstUpdatesWorkDir != null) {
                                    CleanCommand cleanCommand = new CleanCommand.Builder().setLstUpdatesWorkDir(lstUpdatesWorkDir).createCleanCommand();
                                    cleanCommand.executeOp(ctx, this.host);
                                }

                                return CommandResult.SUCCESS;
                            } else if (reply != null && reply.equalsIgnoreCase("y")) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        // In case of an error, clean the cache if there is one
                        if (lstUpdatesWorkDir != null) {
                            CleanCommand cleanCommand = new CleanCommand.Builder().setLstUpdatesWorkDir(lstUpdatesWorkDir).createCleanCommand();
                            cleanCommand.executeOp(ctx, this.host);
                        }

                        return CommandResult.FAILURE;
                    }
                }

                commandInvocation.println("\nThe new installation is being prepared ...\n");
                // trigger an prepare-update
                PrepareUpdateAction.Builder prepareUpdateActionBuilder = new PrepareUpdateAction.Builder()
                        .setNoResolveLocalCache(optNoResolveLocalCache)
                        .setUseDefaultLocalCache(optUseDefaultLocalCache)
                        .setLocalCache(localCache)
                        .setRepositories(repositories)
                        .setOffline(offline)
                        .setListUpdatesWorkDir(lstUpdatesWorkDir)
                        .setHeaders(headers);

                PrepareUpdateAction prepareUpdateAction = prepareUpdateActionBuilder.build();
                ModelNode prepareUpdateResult = prepareUpdateAction.executeOp(ctx, this.host);
                printUpdatesResult(ctx, prepareUpdateResult.get(Util.RESULT));
            }
        } else {
            ctx.printLine("Operation result is not available.");
        }

        return CommandResult.SUCCESS;
    }

    private void printListUpdatesResult(CLICommandInvocation commandInvocation, List<ModelNode> changesMn) {
        if (changesMn.isEmpty()) {
            commandInvocation.println("No updates found");
            return;
        }

        int maxLength = 0;
        for (ModelNode artifactChange : changesMn) {
            String channelName = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString();
            maxLength = Math.max(maxLength, channelName.length());
        }
        maxLength += 1;

        boolean hasDowngrades = false;
        final Set<VersionChange> nonDowngradedUpdatesCache = new HashSet<>();
        commandInvocation.println("Updates found:");
        for (ModelNode change : changesMn) {
            String artifactName = change.get(InstMgrConstants.LIST_UPDATES_ARTIFACT_NAME).asString();
            String oldVersion = change.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).asStringOrNull();
            String newVersion = change.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).asStringOrNull();
            String status = change.get(InstMgrConstants.LIST_UPDATES_STATUS).asStringOrNull();

            boolean isDowngrade = ArtifactChange.Status.UPDATED.toString().toLowerCase(Locale.ENGLISH).equals(status)
                    && isVersionDowngrade(oldVersion, newVersion, nonDowngradedUpdatesCache);
            if (isDowngrade) {
                hasDowngrades = true;
            }

            oldVersion = oldVersion == null ? "[]" : oldVersion;
            newVersion = newVersion == null ? "[]" : newVersion;

            String marker = isDowngrade ? Util.formatWarnMessage("[*] ") : "    ";
            commandInvocation.println(String.format("%s%-" + maxLength + "s %15s ==> %-15s", marker, artifactName,
                    oldVersion, newVersion));
        }

        if (hasDowngrades) {
            commandInvocation.println(Util.formatWarnMessage(
                    "\n[*] The update list contains one or more artifacts with a lower version than the one currently installed. Proceed with caution."));
        }
    }

    private boolean isVersionDowngrade(String previousVersion, String nextVersion,
            Set<VersionChange> nonDowngradedUpdatesCache) {
        if (previousVersion == null || nextVersion == null) {
            return false;
        }

        final VersionChange change = new VersionChange(previousVersion, nextVersion);
        if (nonDowngradedUpdatesCache.contains(change)) {
            return false;
        }
        final boolean isDowngrade = compareVersions(previousVersion, nextVersion) > 0;
        if (!isDowngrade) {
            nonDowngradedUpdatesCache.add(change);
        }
        return isDowngrade;
    }

    // Copied from:
    // https://raw.githubusercontent.com/wildfly/wildfly-channel/refs/heads/main/core/src/main/java/org/wildfly/channel/version/VersionMatcher.java
    // by Jeff Mesnil
    // same implementation is also used in Prospero
    // note that the comparison in "suffix" section is lexicographical, meaning
    // that "1.2.3.Final" is before "1.2.3.RC1", but that should not be an issue
    // since in EAP we are using CR
    private int compareVersions(String previousVersion, String nextVersion) {
        int i1 = 0, i2 = 0;
        final int epoch1;
        int i = previousVersion.indexOf(":");
        if (i != -1) {
            epoch1 = Integer.valueOf(previousVersion.substring(0, i));
            i1 = i;
        } else
            epoch1 = 0;
        final int epoch2;
        i = nextVersion.indexOf(":");
        if (i != -1) {
            epoch2 = Integer.valueOf(nextVersion.substring(0, i));
            i2 = i;
        } else
            epoch2 = 0;
        if (epoch1 != epoch2)
            return epoch1 - epoch2;

        final int lim1 = previousVersion.length(), lim2 = nextVersion.length();
        while (i1 < lim1 && i2 < lim2) {
            final char c1 = previousVersion.charAt(i1);
            final char c2 = nextVersion.charAt(i2);
            if (Character.isDigit(c1) || Character.isDigit(c2)) {
                int ei1 = i1, ei2 = i2;
                while (ei1 < lim1 && Character.isDigit(previousVersion.charAt(ei1)))
                    ei1++;
                while (ei2 < lim2 && Character.isDigit(nextVersion.charAt(ei2)))
                    ei2++;
                final long n1 = ei1 == i1 ? 0 : Long.valueOf(previousVersion.substring(i1, ei1));
                final long n2 = ei2 == i2 ? 0 : Long.valueOf(nextVersion.substring(i2, ei2));
                if (n1 != n2)
                    return (int) (n1 - n2);
                i1 = ei1;
                i2 = ei2;
                if (i1 != i2)
                    return i2 - i1;
            } else if (c1 == c2) {
                i1++;
                i2++;
            } else {
                return c1 - c2;
            }
        }
        return lim1 - lim2;
    };

    private void printUpdatesResult(CommandContext ctx, ModelNode result) {
        if (result.isDefined()) {
            ctx.printLine("The candidate server has been generated. To apply it, restart the server with 'shutdown --perform-installation' command.");
        } else {
            ctx.printLine("The candidate server was not generated as there were no pending updates found.");
        }
    }

    @Override
    protected Operation buildOperation() {
        throw new IllegalStateException("Update Command has not build operation");
    }

    private static class VersionChange {
        private final String previousVersion;
        private final String nextVersion;

        public VersionChange(String previousVersion, String nextVersion) {
            this.previousVersion = previousVersion;
            this.nextVersion = nextVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            VersionChange that = (VersionChange) o;

            if (!previousVersion.equals(that.previousVersion))
                return false;
            return nextVersion.equals(that.nextVersion);
        }

        @Override
        public int hashCode() {
            int result = previousVersion.hashCode();
            result = 31 * result + nextVersion.hashCode();
            return result;
        }
    }
}
