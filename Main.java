package gitlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Jianing Yu
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        try {
            Locale.setDefault(Locale.ENGLISH);
            List<String> argList = Arrays.asList(args);
            if (argList.size() == 0) {
                System.err.println("Please enter a command.");
                System.exit(0);
            }
            Command found = null;
            for (Command command : COMMANDS) {
                if (command._name.equals(argList.get(0))) {
                    found = command;
                    break;
                }
            }
            List<String> operands = argList.subList(1, argList.size());
            if (found == null) {
                System.err.println("No command with that name exists.");
            } else if (!found._validator.test(operands)) {
                System.err.println("Incorrect operands.");
            } else {
                found._processor.accept(operands);
            }
        } catch (GitletException excp) {
            System.err.println(excp.getMessage());
        }
    }

    /**
     * A command.
     */
    static class Command {
        /**
         * Create a command.
         * @param name name.
         * @param validator validator.
         * @param processor processor.
         */
        Command(String name,
                Predicate<List<String>> validator,
                Consumer<List<String>> processor) {
            _name = name;
            _validator = validator;
            _processor = processor;
        }

        /**
         * The command name. Case insensitive.
         */
        private final String _name;
        /**
         * The validator of the arguments.
         */
        private final Predicate<List<String>> _validator;
        /**
         * The implementation of the command.
         */
        private final Consumer<List<String>> _processor;
    }

    /**
     * All supported commands.
     */
    private static final List<Command> COMMANDS = Arrays.asList(
        new Command("init",
            ops -> ops.size() == 0, ops -> init()),
        new Command("add",
            ops -> ops.size() == 1, ops -> add(ops.get(0))),
        new Command("commit",
            ops -> ops.size() == 1, ops -> commit(ops.get(0))),
        new Command("log",
            ops -> ops.size() == 0, ops -> log()),
        new Command("checkout",
            ops -> ops.size() >= 1 && ops.size() <= 3, Main::checkout),
        new Command("rm",
            ops -> ops.size() == 1, ops -> rm(ops.get(0))),
        new Command("global-log",
            ops -> ops.size() == 0, ops -> globalLog()),
        new Command("find",
            ops -> ops.size() == 1, ops -> find(ops.get(0))),
        new Command("status",
            ops -> ops.size() == 0, ops -> status()),
        new Command("branch",
            ops -> ops.size() == 1, ops -> branch(ops.get(0))),
        new Command("rm-branch",
            ops -> ops.size() == 1, ops -> rmBranch(ops.get(0))),
        new Command("reset",
            ops -> ops.size() == 1, ops -> reset(ops.get(0))),
        new Command("merge",
            ops -> ops.size() == 1, ops -> merge(ops.get(0)))
    );

    /*  Commands  */

    /**
     * The init command.
     */
    private static void init() {
        File gitlet = new File(".gitlet");
        if (gitlet.exists()) {
            throw Utils.error("A Gitlet version-control system "
                    + "already exists in the current directory.");
        }

        boolean result = gitlet.mkdir();
        if (!result) {
            throw Utils.error("Unable to create directory");
        }

        Git git = new Git();

        Git.Commit commit = new Git.Commit("initial commit", new Date(0));
        git._commits = new HashMap<>(
                Collections.singletonMap(commit._hash, commit));
        git._branches = new TreeMap<>(
                Collections.singletonMap("master", commit._hash)
        );
        git._currentBranch = "master";
        git._headPtr = commit._hash;
        git._files = new HashMap<>();
        git._staged = new HashMap<>();

        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * The add command.
     * @param filename The file to be added.
     */
    private static void add(String filename) {
        Git git = checkInitialized();

        File file = new File(filename);
        if (!file.exists()) {
            throw Utils.error("File does not exist.");
        }

        byte[] content = Utils.readContents(file);
        String sha1 = Utils.sha1(content);
        git._files.put(sha1, content);
        git._staged.put(file, sha1);

        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * The commit command.
     * @param message The commit message.
     */
    private static void commit(String message) {
        Git git = checkInitialized();

        Git.Commit head = git._commits.get(git._headPtr);
        if (head._committed.equals(git._staged)) {
            throw Utils.error("No changes added to the commit.");
        }
        if (message == null || message.length() == 0) {
            throw Utils.error("Please enter a commit message.");
        }

        Git.Commit commit = new Git.Commit(
                message, new Date(),
                Collections.singletonList(git._headPtr), git._staged);

        git._headPtr = commit._hash;
        git._commits.put(commit._hash, commit);
        git._branches.put(git._currentBranch, commit._hash);

        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * The rm command.
     * @param fileName the filename.
     */
    private static void rm(String fileName) {
        Git git = checkInitialized();

        File file = new File(fileName);
        Git.Commit head = git._commits.get(git._headPtr);
        if (head._committed.containsKey(file)) {
            git._staged.remove(file);
            file.delete();
        } else if (git._staged.containsKey(file)) {
            git._staged.remove(file);
        } else {
            throw Utils.error("No reason to remove the file.");
        }

        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }


    /**
     * The global-log command.
     */
    private static void globalLog() {
        Git git = checkInitialized();

        for (Git.Commit commit : git._commits.values()) {
            logCommit(commit);
        }
    }

    /**
     * The find command.
     * @param message commit message..
     */
    private static void find(String message) {
        Git git = checkInitialized();

        boolean found = false;
        for (Git.Commit commit : git._commits.values()) {
            if (commit._message.equals(message)) {
                found = true;
                System.out.println(commit._hash);
            }
        }
        if (!found) {
            System.err.println("Found no commit with that message.");
        }
    }

    /**
     * The status command.
     */
    private static void status() {
        Git git = checkInitialized();
        System.out.println("=== Branches ===");
        for (String name : git._branches.keySet()) {
            if (name.equals(git._currentBranch)) {
                System.out.print("*");
            }
            System.out.println(name);
        }
        System.out.printf("%n=== Staged Files ===%n");
        Git.Commit head = git._commits.get(git._headPtr);
        Set<String> result = new TreeSet<>();
        for (Map.Entry<File, String> entry : git._staged.entrySet()) {
            File file = entry.getKey();
            if (!head._committed.containsKey(file)
                    || !head._committed.get(file).equals(entry.getValue())) {
                result.add(file.getName());
            }
        }
        for (String s : result) {
            System.out.println(s);
        }
        System.out.printf("%n=== Removed Files ===%n");
        result.clear();
        for (Map.Entry<File, String> entry : head._committed.entrySet()) {
            File file = entry.getKey();
            if (!git._staged.containsKey(file)) {
                result.add(file.getName());
            }
        }
        for (String s : result) {
            System.out.println(s);
        }
        System.out.printf("%n=== Modifications Not Staged For Commit ===%n");
        Map<File, String> sorted = new TreeMap<>(git._staged);
        for (Map.Entry<File, String> entry : sorted.entrySet()) {
            if (!entry.getKey().exists()) {
                System.out.print(entry.getKey().getName());
                System.out.println(" (deleted)");
            } else {
                byte[] origin = git._files.get(entry.getValue());
                byte[] current = Utils.readContents(entry.getKey());

                if (!Arrays.equals(origin, current)) {
                    System.out.print(entry.getKey().getName());
                    System.out.println(" (modified)");
                }
            }
        }
        System.out.printf("%n=== Untracked Files ===%n");
        List<String> files = Utils.plainFilenamesIn(".");
        for (String file : files) {
            if (!git._staged.containsKey(new File(file))) {
                System.out.println(file);
            }
        }
        System.out.println();
    }

    /**
     * The git command.
     */
    private static void log() {
        Git git = checkInitialized();

        String hash = git._headPtr;
        while (hash != null) {
            Git.Commit commit = git._commits.get(hash);
            if (commit == null) {
                break;
            }
            logCommit(commit);
            if (commit._parents.isEmpty()) {
                break;
            }
            hash = commit._parents.get(0);
        }
    }

    /**
     * Logs a commit.
     * @param commit the commit.
     */
    private static void logCommit(Git.Commit commit) {
        SimpleDateFormat format =
                new SimpleDateFormat("E MMM dd HH:mm:ss yyyy Z");
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("===%n"));
        sb.append(String.format("commit %s%n", commit._hash));
        if (commit._parents.size() > 1) {
            sb.append("Merge:");
            for (String parent : commit._parents) {
                sb.append(" ");
                sb.append(parent, 0, 7);
            }
            sb.append(String.format("%n"));
        }
        sb.append(String.format("Date: %s%n", format.format(commit._date)));
        sb.append(String.format("%s%n", commit._message));

        System.out.println(sb.toString());
    }

    /**
     * The checkout command.
     * @param args Arguments of the command.
     */
    private static void checkout(List<String> args) {
        Git git = checkInitialized();
        if (args.size() == 1) {
            String branch = args.get(0);
            if (Objects.equals(branch, git._currentBranch)) {
                throw Utils.error("No need to checkout the current branch.");
            }
            String sha1 = git._branches.get(branch);
            if (sha1 == null) {
                throw Utils.error("No such branch exists.");
            }
            checkoutCommit(git, sha1);
            git._currentBranch = branch;
        } else if (args.get(0).equals("--")) {
            File file = new File(args.get(1));
            Git.Commit commit = git._commits.get(git._headPtr);
            String fileHash = commit._committed.get(file);
            byte[] content = git._files.get(fileHash);
            Utils.writeContents(file, content);
        } else if (args.get(1).equals("--")) {
            String sha1 = args.get(0);
            File file = new File(args.get(2));
            Git.Commit commit = getCommit(git, sha1);
            if (commit == null) {
                throw Utils.error("No commit with that id exists.");
            }
            String fileHash = commit._committed.get(file);
            if (fileHash == null) {
                throw Utils.error("File does not exist in that commit.");
            }
            byte[] content = git._files.get(fileHash);
            Utils.writeContents(file, content);
        } else {
            throw Utils.error("Incorrect operands.");
        }
        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * Checkouts to a commit.
     * @param git git.
     * @param sha1 commit id.
     */
    private static void checkoutCommit(Git git, String sha1) {
        containsUntrackedFiles(git);
        List<String> files = Utils.plainFilenamesIn(".");
        for (String file : files) {
            Utils.restrictedDelete(file);
        }
        Git.Commit commit = git._commits.get(sha1);
        for (Map.Entry<File, String> entry : commit._committed.entrySet()) {
            byte[] content = git._files.get(entry.getValue());
            Utils.writeContents(entry.getKey(), content);
        }
        git._headPtr = sha1;
        git._staged = new HashMap<>(commit._committed);
    }

    /**
     * Whether contains untracked files.
     * @param git git.
     */
    private static void containsUntrackedFiles(Git git) {
        List<String> files = Utils.plainFilenamesIn(".");
        for (String file : files) {
            if (!git._staged.containsKey(new File(file))) {
                throw Utils.error("There is an untracked file in the way;"
                        + " delete it or add it first.");
            }
        }
    }

    /**
     * The branch command.
     * @param name branch name.
     */
    private static void branch(String name) {
        Git git = checkInitialized();
        if (git._branches.containsKey(name)) {
            throw Utils.error("A branch with that name already exists.");
        }
        git._branches.put(name, git._headPtr);
        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * The rm-branch command.
     * @param name branch name.
     */
    private static void rmBranch(String name) {
        Git git = checkInitialized();
        if (!git._branches.containsKey(name)) {
            throw Utils.error("A branch with that name does not exist.");
        }
        if (Objects.equals(name, git._currentBranch)) {
            throw Utils.error("Cannot remove the current branch.");
        }
        git._branches.remove(name);
        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * The reset command.
     * @param sha1 commit id.
     */
    private static void reset(String sha1) {
        Git git = checkInitialized();
        Git.Commit commit = getCommit(git, sha1);
        if (commit == null) {
            throw Utils.error("No commit with that id exists.");
        }
        checkoutCommit(git, commit._hash);
        git._branches.put(git._currentBranch, commit._hash);
        File gitFile = Utils.join(".gitlet", "git");
        Utils.writeObject(gitFile, git);
    }

    /**
     * The merge command.
     * @param other another branch.
     */
    private static void merge(String other) {
        Git git = checkInitialized();

        Git.Commit head = git._commits.get(git._headPtr);
        if (!git._staged.keySet().equals(head._committed.keySet())) {
            throw Utils.error("You have uncommitted changes.");
        }
        if (Objects.equals(other, git._currentBranch)) {
            throw Utils.error("Cannot merge a branch with itself.");
        }
        String otherBranch = git._branches.get(other);
        if (otherBranch == null) {
            throw Utils.error("A branch with that name does not exist.");
        }
        containsUntrackedFiles(git);
        String ancestor = ancestorOf(git, git._headPtr, otherBranch);

        if (ancestor.equals(otherBranch)) {
            System.out.println("Given branch is an "
                    + "ancestor of the current branch.");
        } else if (ancestor.equals(git._headPtr)) {
            System.out.println("Current branch fast-forwarded.");
            git._headPtr = otherBranch;
            git._branches.put(git._currentBranch, otherBranch);
            checkoutCommit(git, otherBranch);
            File gitFile = Utils.join(".gitlet", "git");
            Utils.writeObject(gitFile, git);
        } else {
            merge(other, git, head, otherBranch, ancestor);
        }
    }

    /**
     * Merge.
     * @param other other branch.
     * @param git git.
     * @param head head.
     * @param otherBranch other branch.
     * @param ancestor ancestor.
     */
    private static void merge(String other, Git git, Git.Commit head,
                              String otherBranch, String ancestor) {
        Git.Commit split = git._commits.get(ancestor);
        Git.Commit otherHead = git._commits.get(otherBranch);
        boolean conflict = false, needCommit = false;
        for (Map.Entry<File, String> entry : split._committed.entrySet()) {
            File file = entry.getKey();
            String splitHash = entry.getValue();
            String headHash = head._committed.get(file);
            String otherHash = otherHead._committed.get(file);
            if (Objects.equals(headHash, splitHash) && otherHash != null
                && !Objects.equals(otherHash, splitHash)) {
                byte[] content = git._files.get(otherHash);
                Utils.writeContents(file, content);
                git._staged.put(file, otherHash);
                needCommit = true;
            } else if (Objects.equals(headHash, splitHash)
                && otherHash == null) {
                Utils.restrictedDelete(file);
                git._staged.remove(file);
                needCommit = true;
            } else if (!Objects.equals(headHash, splitHash)
                && !Objects.equals(otherHash, splitHash)
                && !Objects.equals(otherHash, headHash)) {
                conflict(git, file, headHash, otherHash);
                needCommit = true;
                conflict = true;
            }
        }
        for (Map.Entry<File, String> entry : otherHead._committed.entrySet()) {
            if (split._committed.containsKey(entry.getKey())) {
                continue;
            }
            File file = entry.getKey();
            String headHash = head._committed.get(file);
            String otherHash = otherHead._committed.get(file);
            if (headHash == null) {
                byte[] content = git._files.get(otherHash);
                Utils.writeContents(file, content);
                git._staged.put(file, otherHash);
                needCommit = true;
            } else if (!Objects.equals(headHash, otherHash)) {
                conflict(git, file, headHash, otherHash);
                needCommit = true;
                conflict = true;
            }
        }
        if (needCommit) {
            String message = String.format("Merged %s into %s.",
                    other, git._currentBranch);
            Git.Commit commit = new Git.Commit(message, new Date(),
                    Arrays.asList(git._headPtr, otherBranch), git._staged);
            git._headPtr = commit._hash;
            git._commits.put(commit._hash, commit);
            File gitFile = Utils.join(".gitlet", "git");
            Utils.writeObject(gitFile, git);
        }
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /**
     * handle conflict.
     * @param git git.
     * @param file file.
     * @param headHash head hash.
     * @param otherHash other hash.
     */
    private static void conflict(Git git, File file,
                                 String headHash, String otherHash) {
        byte[] headBytes = git._files.get(headHash);
        byte[] otherBytes = git._files.get(otherHash);
        byte[] concat = concat(headBytes, otherBytes);
        Utils.writeContents(file, concat);
        String sha1 = Utils.sha1(concat);
        git._files.put(sha1, concat);
        git._staged.put(file, sha1);
    }

    /**
     * Concat conflict files.
     * @param head head version.
     * @param given given version.
     * @return concat result.
     */
    private static byte[] concat(byte[] head, byte[] given) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out, true)) {
            writer.println("<<<<<<< HEAD");
            if (head != null) {
                out.write(head);
            }
            writer.println("=======");
            if (given != null) {
                out.write(given);
            }
            writer.println(">>>>>>>");
            return out.toByteArray();
        } catch (IOException excp) {
            throw new IllegalArgumentException(excp.getMessage());
        }
    }

    /**
     * Ancestor of commits.
     * @param git git.
     * @param headPtr head.
     * @param otherBranch another branch.
     * @return ancestor.
     */
    private static String ancestorOf(Git git,
                                     String headPtr, String otherBranch) {
        Set<String> ancestors = new HashSet<>();
        ancestorsOf(git, ancestors, otherBranch);

        List<String> commitIds = new ArrayList<>();
        commitIds.add(headPtr);
        do {
            String sha1 = commitIds.remove(0);
            if (ancestors.contains(sha1)) {
                return sha1;
            } else {
                Git.Commit commit = git._commits.get(sha1);
                commitIds.addAll(commit._parents);
            }
        } while (commitIds.size() != 0);
        throw Utils.error("bug.");
    }

    /**
     * Ancestor of commits.
     * @param git git.
     * @param ancestors set.
     * @param current current.
     */
    private static void ancestorsOf(Git git,
                                    Set<String> ancestors,
                                    String current) {
        ancestors.add(current);
        Git.Commit commit = git._commits.get(current);
        if (commit._parents != null && !commit._parents.isEmpty()) {
            for (String parent : commit._parents) {
                ancestorsOf(git, ancestors, parent);
            }
        }
    }

    /**
     * Get a commit.
     * @param git git.
     * @param sha1 hash.
     * @return commit.
     */
    private static Git.Commit getCommit(Git git, String sha1) {
        if (sha1.length() == Utils.UID_LENGTH) {
            return git._commits.get(sha1);
        } else {
            for (Git.Commit commit : git._commits.values()) {
                if (commit._hash.startsWith(sha1)) {
                    return commit;
                }
            }
        }
        return null;
    }

    /**
     * Checks if it's a git-controlled folder.
     * @return the git instance.
     */
    private static Git checkInitialized() {
        File gitlet = new File(".gitlet");
        if (!gitlet.exists()) {
            throw Utils.error("Not in an initialized Gitlet directory.");
        }
        return Utils.readObject(Utils.join(gitlet, "git"), Git.class);
    }

    /**
     * A git instance.
     */
    static class Git implements Serializable {
        private static final long serialVersionUID = -368466168212979819L;

        /**
         * The head pointer.
         */
        private String _headPtr;

        /**
         * The current branch.
         */
        private String _currentBranch;
        /**
         * All commits.
         */
        private Map<String, Commit> _commits;
        /**
         * All branches.
         */
        private Map<String, String> _branches;
        /**
         * Staged files.
         */
        private Map<File, String> _staged;
        /**
         * All files.
         */
        private Map<String, byte[]> _files;

        /**
         * A commit.
         */
        static class Commit implements Serializable {
            private static final long serialVersionUID = 696628061159317343L;

            /**
             * Create a commit.
             * @param message The commit message.
             * @param date The commit date.
             */
            Commit(String message, Date date) {
                this._message = message;
                this._date = date;
                this._parents = new ArrayList<>();
                this._committed = new HashMap<>();
                this._hash = Utils.sha1("");
            }

            /**
             * Create a commit.
             * @param message The commit message.
             * @param date The commit date.
             * @param parents The parent commit.
             * @param committed The committed files.
             */
            Commit(String message, Date date,
                   List<String> parents, Map<File, String> committed) {
                this._message = message;
                this._date = date;
                this._committed = new HashMap<>(committed);
                this._parents = parents;
                String hash = Utils.sha1(committed.values());
                List<String> list = new ArrayList<>();
                for (File file : committed.keySet()) {
                    String s = file.toString();
                    list.add(s);
                }
                hash = Utils.sha1(hash, Utils.sha1(list));
                for (String parent : parents) {
                    hash = Utils.sha1(hash, parent);
                }
                this._hash = hash;
            }

            /**
             * The commit hash.
             */
            private String _hash;
            /**
             * The commit message.
             */
            private String _message;
            /**
             * The commit date.
             */
            private Date _date;
            /**
             * The parent commits.
             */
            private List<String> _parents;
            /**
             * Committed files.
             */
            private Map<File, String> _committed;
        }

    }
}
