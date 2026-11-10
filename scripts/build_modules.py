import subprocess
import os
import sys

JAVA_HOME = "/home/agent/agent-work/bin/jdk-17"
KOTLIN_HOME = "/home/agent/agent-work/bin/kotlinc-2.1.0"
COMP_PLUGIN = f"{KOTLIN_HOME}/lib/compose-compiler-plugin.jar"
ANDROID_JAR = "/home/agent/agent-work/bin/android-sdk/platforms/android-34/android.jar"
COMPOSE_DIR = "/home/agent/agent-work/scratch/compose_classpath"
BUILD_CLASSES = "/home/agent/agent-work/scratch/build_out/classes"

os.environ["JAVA_HOME"] = JAVA_HOME
os.environ["PATH"] = f"{JAVA_HOME}/bin:" + os.environ["PATH"]

compose_jars = [os.path.join(COMPOSE_DIR, f) for f in os.listdir(COMPOSE_DIR) if f.endswith(".jar")]
compose_cp = ":".join(compose_jars)

MODULES = [
    ("watchawatch_models", ["/home/agent/agent-work/projects/watchawatch/app/src/main/java/com/watchawatch/app/Models.kt"], False),
    ("watchawatch_timeline", ["/home/agent/agent-work/projects/watchawatch/app/src/main/java/com/watchawatch/app/SessionTimeline.kt"], False),
    ("watchawatch_insights", ["/home/agent/agent-work/projects/watchawatch/app/src/main/java/com/watchawatch/app/LocalInsights.kt"], False),
    ("watchawatch_csv", ["/home/agent/agent-work/projects/watchawatch/app/src/main/java/com/watchawatch/app/CsvTransfer.kt"], False),
    ("watchawatch_utils", ["/home/agent/agent-work/scratch/split_src/Utils.kt"], True),
    ("watchawatch_service", ["/home/agent/agent-work/projects/watchawatch/app/src/main/java/com/watchawatch/app/FloatingTimerService.kt"], False),
    ("watchawatch_viewmodel", ["/home/agent/agent-work/scratch/split_src/ViewModel.kt"], False),
    ("watchawatch_components", ["/home/agent/agent-work/scratch/split_src/Components.kt"], True),
    ("watchawatch_dialogs", ["/home/agent/agent-work/scratch/split_src/Dialogs.kt"], True),
    ("watchawatch_sessions", ["/home/agent/agent-work/scratch/split_src/SessionsScreen.kt"], True),
    ("watchawatch_projects", ["/home/agent/agent-work/scratch/split_src/ProjectsScreen.kt"], True),
    ("watchawatch_reports", ["/home/agent/agent-work/scratch/split_src/ReportsScreen.kt"], True),
    ("watchawatch_settings", ["/home/agent/agent-work/scratch/split_src/SettingsScreen.kt"], True),
    ("watchawatch_today", ["/home/agent/agent-work/scratch/split_src/TodayScreen.kt"], True),
    ("watchawatch_main", ["/home/agent/agent-work/scratch/split_src/MainActivity.kt"], True),
]

# We package BUILD_CLASSES into a jar before each step so kotlinc gets all kotlin_modules reliably
jar_path = "/home/agent/agent-work/scratch/all_built_classes.jar"

for mod_name, src_files, needs_compose in MODULES:
    # First update jar
    subprocess.run([f"{JAVA_HOME}/bin/jar", "cf", jar_path, "-C", BUILD_CLASSES, "."], check=True)
    
    cp = f"{ANDROID_JAR}:{jar_path}:{compose_cp}"
    cmd = [
        f"{JAVA_HOME}/bin/java",
        "-Xmx200m",
        "-XX:MaxMetaspaceSize=90m",
        "-XX:+UseSerialGC",
        "-XX:TieredStopAtLevel=1",
        "-cp", f"{KOTLIN_HOME}/lib/kotlin-compiler.jar",
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-module-name", mod_name,
        "-cp", cp,
        "-Xskip-metadata-version-check",
        "-d", BUILD_CLASSES
    ]
    if needs_compose:
        cmd.extend(["-Xplugin=" + COMP_PLUGIN])
    cmd.extend(src_files)
    
    print(f"--> Compiling {mod_name} ({len(src_files)} files)...", flush=True)
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if res.returncode != 0:
        print(f"FAILED {mod_name}:\n{res.stdout}")
        sys.exit(1)
    else:
        print(f"SUCCESS {mod_name}")

# Final jar update
subprocess.run([f"{JAVA_HOME}/bin/jar", "cf", jar_path, "-C", BUILD_CLASSES, "."], check=True)
print("ALL MODULES COMPILED SUCCESSFULLY!")
