package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import antlr.ANTLRException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.triggers.SCMTrigger;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationBuildWrapper;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.GitBridge;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

/**
 * Created by andrius on 9/2/14.
 */
public class SquashCommitStrategyIT {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private final File GIT_DIR = new File("test-repo/.git");
    private final File GIT_PARENT_DIR = GIT_DIR.getParentFile().getAbsoluteFile();
    private final String README_FILE_PATH = GIT_PARENT_DIR.getPath().concat("/" + "readme");

    private final String AUTHER_NAME = "john Doe";
    private final String AUTHER_EMAIL = "Joh@praqma.net";

    Repository repository;

    private String readmeFileContents_fromDevBranch;


    public void createValidRepository() throws IOException, GitAPIException {
        final String FEATURE_BRANCH_NAME = "ready/feature_1";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        Git git = new Git(repository);

        File readme = new File(README_FILE_PATH);
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n");

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
    }

    @After
    public void tearDown() throws IOException {
        repository.close();
        FileUtils.deleteDirectory(GIT_PARENT_DIR);
    }


    private void createRepositoryWithMergeConflict() throws IOException, GitAPIException {
        final String FEATURE_BRANCH_NAME = "ready/feature_1";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        repository = builder.setGitDir(GIT_DIR.getAbsoluteFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();

        if (!repository.isBare() && repository.getBranch() == null) {
            repository.create();
        }

        Git git = new Git(repository);

        File readme = new File(README_FILE_PATH);
        if (!readme.exists())
            FileUtils.writeStringToFile(readme, "sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 1").call();

        FileUtils.writeStringToFile(readme, "changed sample text\n");

        git.add().addFilepattern(readme.getName()).call();
        git.commit().setMessage("commit message 2").call();

        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand.setName(FEATURE_BRANCH_NAME);
        createBranchCommand.call();

        git.checkout().setName(FEATURE_BRANCH_NAME).call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 1\n");

        git.add().addFilepattern(readme.getName()).call();
        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        FileUtils.writeStringToFile(readme, "FEATURE_1 branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("feature 1 commit 2");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        git.checkout().setName("master").call();

        FileUtils.writeStringToFile(readme, "Merge conflict branch commit 2\n");

        git.add().addFilepattern(readme.getName()).call();
        commitCommand = git.commit();
        commitCommand.setMessage("merge conflict message 1");
        commitCommand.setAuthor(AUTHER_NAME, AUTHER_EMAIL);
        commitCommand.call();

        readmeFileContents_fromDevBranch = FileUtils.readFileToString(new File(README_FILE_PATH));
    }

    private FreeStyleProject configurePretestedIntegrationPlugin() throws IOException, ANTLRException, InterruptedException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();

        GitBridge gitBridge = new GitBridge(new SquashCommitStrategy(), "master");

        project.getBuildWrappersList().add(new PretestedIntegrationBuildWrapper(gitBridge));
        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();
        repoList.add(new UserRemoteConfig("file://" + GIT_DIR.getAbsolutePath(), null, null, null));

        List<GitSCMExtension> gitSCMExtensions = new ArrayList<GitSCMExtension>();
        gitSCMExtensions.add(new PruneStaleBranch());
        gitSCMExtensions.add(new CleanCheckout());

        GitSCM gitSCM = new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("origin/ready/**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, gitSCMExtensions);

        project.setScm(gitSCM);

        SCMTrigger scmTrigger = new SCMTrigger("@daily", true);
        project.addTrigger(scmTrigger);

        scmTrigger.start(project, true);
        scmTrigger.new Runner().run();

        Thread.sleep(1000);

        return project;
    }

    @Test
    public void canMergeAnIntegrationBranch() throws IOException, ANTLRException, InterruptedException {
        try {
            createValidRepository();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }

        FreeStyleProject project = configurePretestedIntegrationPlugin();

        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        QueueTaskFuture<Queue.Executable> future = jenkinsRule.jenkins.getQueue().getItems()[0].getFuture();

        do {
            Thread.sleep(1000);
        } while (!future.isDone());

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(README_FILE_PATH));
        assertEquals(readmeFileContents_fromDevBranch, readmeFileContents);
    }

    @Test
    public void ShouldFailWithAMergeConflictPresent() throws Exception {
        try {
            createRepositoryWithMergeConflict();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }

        FreeStyleProject project = configurePretestedIntegrationPlugin();

        assertEquals(1, jenkinsRule.jenkins.getQueue().getItems().length);

        QueueTaskFuture<Queue.Executable> future = jenkinsRule.jenkins.getQueue().getItems()[0].getFuture();

        do {
            Thread.sleep(1000);
        } while (!future.isDone());

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));
    }
}
