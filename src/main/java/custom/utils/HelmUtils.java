package custom.utils;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helm 命令行操作工具类。
 * <p>
 * 通过 ProcessBuilder 执行 helm 命令。
 * <p>
 * 注意：运行环境需要安装 helm CLI (v3.x)
 */
@Slf4j
public class HelmUtils {

    /**
     * 添加 Helm 仓库
     *
     * @param repoName 仓库名称
     * @param repoUrl  仓库 URL
     * @return 命令执行结果
     */
    public static CommandResult addRepo(String repoName, String repoUrl) {
        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("repo");
        command.add("add");
        command.add(repoName);
        command.add(repoUrl);
        command.add("--force-update");

        return executeCommand(command.toArray(new String[0]), 5);
    }

    /**
     * 更新 Helm 仓库
     *
     * @return 命令执行结果
     */
    public static CommandResult updateRepo() {
        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("repo");
        command.add("update");

        return executeCommand(command.toArray(new String[0]), 5);
    }

    /**
     * 安装 Helm Chart
     *
     * @param releaseName  Release 名称
     * @param chartName    Chart 名称（如 milvus/milvus）
     * @param namespace    命名空间
     * @param createNs     是否创建命名空间
     * @param chartVersion Chart 版本（可选，为空使用最新）
     * @param setValues    --set 参数 Map
     * @param kubeconfig   kubeconfig 文件路径（可选）
     * @param timeout      超时时间（分钟）
     * @return 命令执行结果
     */
    public static CommandResult install(
            String releaseName,
            String chartName,
            String namespace,
            boolean createNs,
            String chartVersion,
            Map<String, String> setValues,
            String kubeconfig,
            int timeout) {

        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("install");
        command.add(releaseName);
        command.add(chartName);
        command.add("--namespace");
        command.add(namespace);

        if (createNs) {
            command.add("--create-namespace");
        }

        if (chartVersion != null && !chartVersion.isEmpty()) {
            command.add("--version");
            command.add(chartVersion);
        }

        if (setValues != null && !setValues.isEmpty()) {
            for (Map.Entry<String, String> entry : setValues.entrySet()) {
                // labels 的值必须是字符串，使用 --set-string 避免 "true" 被解析为 boolean
                if (entry.getKey().startsWith("labels.")) {
                    command.add("--set-string");
                } else {
                    command.add("--set");
                }
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        if (kubeconfig != null && !kubeconfig.isEmpty()) {
            command.add("--kubeconfig");
            command.add(kubeconfig);
        }

        command.add("--wait");
        command.add("--timeout");
        command.add(timeout + "m");

        log.info("Executing helm install command: " + String.join(" ", command));
        return executeCommand(command.toArray(new String[0]), timeout + 5);
    }

    /**
     * 升级 Helm Release
     *
     * @param releaseName  Release 名称
     * @param chartName    Chart 名称
     * @param namespace    命名空间
     * @param chartVersion Chart 版本（可选）
     * @param setValues    --set 参数 Map
     * @param kubeconfig   kubeconfig 文件路径（可选）
     * @param timeout      超时时间（分钟）
     * @return 命令执行结果
     */
    public static CommandResult upgrade(
            String releaseName,
            String chartName,
            String namespace,
            String chartVersion,
            Map<String, String> setValues,
            String kubeconfig,
            int timeout) {

        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("upgrade");
        command.add(releaseName);
        command.add(chartName);
        command.add("--namespace");
        command.add(namespace);

        if (chartVersion != null && !chartVersion.isEmpty()) {
            command.add("--version");
            command.add(chartVersion);
        }

        if (setValues != null && !setValues.isEmpty()) {
            for (Map.Entry<String, String> entry : setValues.entrySet()) {
                command.add("--set");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        if (kubeconfig != null && !kubeconfig.isEmpty()) {
            command.add("--kubeconfig");
            command.add(kubeconfig);
        }

        command.add("--wait");
        command.add("--timeout");
        command.add(timeout + "m");

        log.info("Executing helm upgrade command: " + String.join(" ", command));
        return executeCommand(command.toArray(new String[0]), timeout + 5);
    }

    /**
     * 卸载 Helm Release
     *
     * @param releaseName Release 名称
     * @param namespace   命名空间
     * @param kubeconfig  kubeconfig 文件路径（可选）
     * @return 命令执行结果
     */
    public static CommandResult uninstall(
            String releaseName,
            String namespace,
            String kubeconfig) {

        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("uninstall");
        command.add(releaseName);
        command.add("--namespace");
        command.add(namespace);

        if (kubeconfig != null && !kubeconfig.isEmpty()) {
            command.add("--kubeconfig");
            command.add(kubeconfig);
        }

        log.info("Executing helm uninstall command: " + String.join(" ", command));
        return executeCommand(command.toArray(new String[0]), 10);
    }

    /**
     * 获取 Release 状态
     *
     * @param releaseName Release 名称
     * @param namespace   命名空间
     * @param kubeconfig  kubeconfig 文件路径（可选）
     * @return 命令执行结果（JSON 格式）
     */
    public static CommandResult status(
            String releaseName,
            String namespace,
            String kubeconfig) {

        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("status");
        command.add(releaseName);
        command.add("--namespace");
        command.add(namespace);
        command.add("-o");
        command.add("json");

        if (kubeconfig != null && !kubeconfig.isEmpty()) {
            command.add("--kubeconfig");
            command.add(kubeconfig);
        }

        return executeCommand(command.toArray(new String[0]), 2);
    }

    /**
     * 列出 Release
     *
     * @param namespace  命名空间（为空列出所有）
     * @param kubeconfig kubeconfig 文件路径（可选）
     * @return 命令执行结果（JSON 格式）
     */
    public static CommandResult list(String namespace, String kubeconfig) {
        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("list");

        if (namespace != null && !namespace.isEmpty()) {
            command.add("--namespace");
            command.add(namespace);
        } else {
            command.add("--all-namespaces");
        }

        command.add("-o");
        command.add("json");

        if (kubeconfig != null && !kubeconfig.isEmpty()) {
            command.add("--kubeconfig");
            command.add(kubeconfig);
        }

        return executeCommand(command.toArray(new String[0]), 2);
    }

    /**
     * 检查 Release 是否存在
     *
     * @param releaseName Release 名称
     * @param namespace   命名空间
     * @param kubeconfig  kubeconfig 文件路径（可选）
     * @return 是否存在
     */
    public static boolean releaseExists(String releaseName, String namespace, String kubeconfig) {
        CommandResult result = status(releaseName, namespace, kubeconfig);
        return result.isSuccess();
    }

    /**
     * 执行命令的通用方法
     *
     * @param command        命令数组
     * @param timeoutMinutes 超时时间（分钟）
     * @return CommandResult
     */
    private static CommandResult executeCommand(String[] command, int timeoutMinutes) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = -1;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 读取标准输出
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                        log.debug("[stdout] " + line);
                    }
                } catch (Exception e) {
                    log.error("Error reading stdout", e);
                }
            });

            // 读取标准错误
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                        log.debug("[stderr] " + line);
                    }
                } catch (Exception e) {
                    log.error("Error reading stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                stderr.append("Command timed out after " + timeoutMinutes + " minutes");
                log.error("Command timed out: " + String.join(" ", command));
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            exitCode = finished ? process.exitValue() : -1;

        } catch (Exception e) {
            log.error("Failed to execute command: " + String.join(" ", command), e);
            stderr.append("Exception: ").append(e.getMessage());
        }

        return CommandResult.builder()
                .exitCode(exitCode)
                .stdout(stdout.toString().trim())
                .stderr(stderr.toString().trim())
                .success(exitCode == 0)
                .build();
    }

    /**
     * 命令执行结果封装
     */
    @Data
    @Builder
    public static class CommandResult {
        int exitCode;
        String stdout;
        String stderr;
        boolean success;
    }
}
