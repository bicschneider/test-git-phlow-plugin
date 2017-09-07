package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import com.google.common.io.Files;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import static junit.framework.TestCase.assertNotNull;
import org.apache.commons.collections.MapUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Base test class for all test methods using static git test repositories.
 * To re-use and simplify using the zipped repositories, we have created a this 
 * base test class to use in our tests.
 * It have to primary goals:
 * 1. Supply test methods with common setup for using the static git repositories:
 *  Implement the setUp method, inherited by test-classes and used as setup method for each test. 
 *  The setup method make objects like a bare git repository and a working git repository available. 
 *  Used respectively in the Jenkins job, and as workspace to verify tests.
 * 2. Contain a map over which test methods using which static git repositories: 
 *  We have created a hash map, that for each test method that uses a static git 
 *  repository specify which git repository zip-file to use from the default test 
 *  resources. The HashMap serves two purposes: 
 *      1) enabling automatic loop-up of zip-file to use in the setUp method to
 *         avoid parsing parameters 
 *      2) give a simple overview of which repositories are used 
 *         where (we plan for reuse).
 */
public class StaticGitRepositoryTestBase {

    // Tell which test method name uses which git repository and the setUp method
    // will do the magic.
    HashMap<String, String> testMethodName_vs_staticGitRepoName = new HashMap();

    public StaticGitRepositoryTestBase() {
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesSquashedLinux", "commitMessagesWithDoubleQuotes_linux");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesAccumulatedLinux", "commitMessagesWithDoubleQuotes_linux");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesSquashedWindows", "commitMessagesWithDoubleQuotes_windows");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesAccumulatedWindows", "commitMessagesWithDoubleQuotes_windows");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsAccumulated", "commitMessagesWithDoubleQuotesSingleQuotesMade_windows");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsSquashed", "commitMessagesWithDoubleQuotesSingleQuotesMade_windows");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsAccumulated_customerSuppliedRepo", "JENKINS-28640");
        testMethodName_vs_staticGitRepoName.put("commitMessagesWithDoubleQuotesSingleQuotesMadeWindowsSquashed_customerSuppliedRepo", "JENKINS-28640");
        testMethodName_vs_staticGitRepoName.put("authorOfLastCommitUsedIfMoreThanOneCommitSquashStrategy", "useAuthorOfLastCommit");
        testMethodName_vs_staticGitRepoName.put("authorOfLastCommitUsedIfMoreThanOneCommitAccumulatedStrategy", "useAuthorOfLastCommit");
        testMethodName_vs_staticGitRepoName.put("customIntegrationBranchSquashStrategy", "customIntegrationBranch");
        testMethodName_vs_staticGitRepoName.put("customIntegrationBranchAccumulatedStrategy", "customIntegrationBranch");
    }

    public File tempFolder;
    public String temporaryFolder;
    public Repository bareRepository;
    Git gitrepo;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Rule
    public TestName name = new TestName();

    /**
     * The setUp method make a git repository available for testing, and creates
     * the git repository working clone and the bare git repository to use in
     * the Jenkins job setup. The method uses the hash map to look up which
     * static git repository (those generated by scripts, included as test
     * resources and zipped) to use for a specific test method. Debug printing
     * is done on the way...
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        // creates a temporary folder using Google common io utils
        tempFolder = Files.createTempDir();
        temporaryFolder = tempFolder.toString();

        // method name of the test method that uses this setUp method NOW!
        String methodName = name.getMethodName();
        System.out.println(String.format("**********************************************************************"));
        System.out.println(String.format("***** setUp for test %s", methodName));
        System.out.println(String.format("**********************************************************************"));
        System.out.println(String.format("* Temporary test working folder:          %s", temporaryFolder));

        // look-up which git repository to use for this test:
        String gitRepoName = testMethodName_vs_staticGitRepoName.get(methodName);
        if (gitRepoName == null) { // only print if failing
            MapUtils.debugPrint(System.out, "testMethodName_vs_staticGitRepoName: ", testMethodName_vs_staticGitRepoName);
            assertNotNull(String.format("setUp method for %s could not look-up static git repo to use based on test method name", methodName), gitRepoName);
        }
        System.out.println(String.format("* Static git repository NAME:             %s", gitRepoName));

        // get resources from test resources, default package, where the zip-files with static git repos reside
        URI zipFileURI = TestUtilsFactory.class.getClassLoader().getResource(gitRepoName + ".zip").toURI();
        System.out.println(String.format("* Using static repository from zip file:    %s", zipFileURI));

        // By convention, the way we pack our repository they are unpacked as this:
        String gitRepo = temporaryFolder + "/" + gitRepoName; // working clone, crated below
        String gitRepoBare = gitRepo + ".git";
        System.out.println(String.format("* BARE repository is                      %s", gitRepo));
        System.out.println(String.format("* Working repository is                   %s", gitRepo));

        String zipfile = new File(zipFileURI).toString();
        TestUtilsFactory.unzipFunction(temporaryFolder, zipfile);

        // bare repository is used as the repository you point Jenkins job to poll
        bareRepository = new FileRepository(gitRepoBare);
        File workingRepoPath = new File(gitRepo);

        // clone bare repository - use it in tests to checkout integrated coomits and verify them.
        Git.cloneRepository().setURI("file:///" + gitRepoBare).setDirectory(workingRepoPath)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
        // Open it
        gitrepo = Git.open(workingRepoPath);
        System.out.println(String.format("**********************************************************************"));
        System.out.println(String.format("***** setUp for test %s", methodName));
        System.out.println(String.format("**********************************************************************"));
    }

    /**
     * Tear down used to delete temporary folder where git repository for tests
     * reside.
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        String methodName = name.getMethodName();
        System.out.println(String.format("**********************************************************************"));
        System.out.println(String.format("***** tearDown for test %s", methodName));
        gitrepo.close();
        bareRepository.close();
        // Repos reside inside temporary folder pr. test, clean up
        try {
            TestUtilsFactory.destroyDirectory(tempFolder,300,5);
            tempFolder.delete();
        } catch ( Exception e ){
            System.out.format("WARNING: Could not delete the directory: " + tempFolder.getAbsolutePath() );
        }

        // method name of the test method that uses this setUp method NOW!
        System.out.println(String.format("***** DONE tearDown for test %s", methodName));
        System.out.println(String.format("**********************************************************************"));
    }
}
