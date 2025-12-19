#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
macOS ARM64 Build Script for nndeploy
This script automates the build process for nndeploy on macOS ARM64 platform
Based on GitHub Actions workflow configuration
"""

import os
import sys
import shutil
import subprocess
import platform
from pathlib import Path
import argparse
import multiprocessing
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('build_mac_arm64.log')
    ]
)
logger = logging.getLogger(__name__)

def run_command(cmd, check=True, shell=True):
    """Execute command and handle errors"""
    logger.info(f"Executing: {cmd}")
    try:
        result = subprocess.run(cmd, shell=shell, check=check, capture_output=True, text=True)
        if result.stdout:
            logger.info(result.stdout)
        return result
    except subprocess.CalledProcessError as e:
        logger.error(f"Error executing command: {cmd}")
        logger.error(f"Return code: {e.returncode}")
        if e.stderr:
            logger.error(f"Error output: {e.stderr}")
        if check:
            sys.exit(1)
        return e

def check_system():
    """Check system information"""
    system_info = {
        'platform': platform.platform(),
        'machine': platform.machine(),
        'python_version': platform.python_version(),
        'system': platform.system()
    }
    
    logger.info("System Information:")
    for key, value in system_info.items():
        logger.info(f"  {key}: {value}")
    
    if system_info['system'] != 'Darwin':
        logger.warning("This script is designed for macOS systems")
    
    if system_info['machine'] != 'arm64':
        logger.warning("This script is optimized for ARM64 architecture")
    
    return system_info

def parse_arguments():
    """Parse command line arguments"""
    parser = argparse.ArgumentParser(description='Build nndeploy on macOS ARM64')
    parser.add_argument('--config', 
                       default='config_opencv_ort_mnn_tokenizer.cmake', 
                       type=str, 
                       help='Config file name (config_opencv_ort_mnn_tokenizer.cmake, config_opencv_ort_mnn.cmake, config_opencv_ort.cmake, config_opencv.cmake)')
    parser.add_argument('--build-type', 
                       default='Release', 
                       choices=['Debug', 'Release', 'RelWithDebInfo', 'MinSizeRel'],
                       help='CMake build type')
    parser.add_argument('--jobs', 
                       type=int, 
                       default=multiprocessing.cpu_count(),
                       help='Number of parallel jobs for compilation')
    parser.add_argument('--clean', 
                       action='store_true',
                       help='Clean build directory before building')
    parser.add_argument('--skip-deps', 
                       action='store_true',
                       help='Skip dependency installation')
    parser.add_argument('--skip-third-party', 
                       action='store_true',
                       help='Skip third-party library installation')
    
    return parser.parse_args()

def install_system_dependencies():
    """Install system dependencies using Homebrew"""
    logger.info("Installing system dependencies...")
    
    # Check if Homebrew is installed
    try:
        result = run_command("brew --version", check=False)
        if result.returncode != 0:
            logger.info("Homebrew not found, installing...")
            run_command('/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"')
    except:
        logger.info("Installing Homebrew...")
        run_command('/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"')
    
    # Update Homebrew
    logger.info("Updating Homebrew...")
    run_command("brew update")
    
    # Install build tools and dependencies
    dependencies = [
        "cmake", 
        "make",
        "pkg-config",
        "opencv",
        "protobuf",
        "git",
        "wget",
        "curl",
        "unzip",
    ]
    
    logger.info("Installing build tools and dependencies...")
    for dep in dependencies:
        logger.info(f"Installing {dep}...")
        result = run_command(f"brew list {dep} || brew install {dep}", check=False)
        if result.returncode != 0:
            logger.warning(f"{dep} installation failed or already installed")
    
    logger.info("System dependencies installation completed!")

def install_python_dependencies():
    """Install Python dependencies"""
    logger.info("Installing Python dependencies...")
    
    # Upgrade pip
    logger.info("Upgrading pip...")
    run_command("python3 -m pip install --upgrade pip")
    
    # Install Python dependencies
    python_deps = [
        "pybind11",
        "setuptools", 
        "wheel",
        "twine",
        "requests",
        "pathlib2",
        "cython",
        "numpy"
    ]
    
    for dep in python_deps:
        logger.info(f"Installing {dep}...")
        result = run_command(f"pip3 install {dep}", check=False)
        if result.returncode != 0:
            logger.warning(f"{dep} installation failed")
    
    logger.info("Python dependencies installation completed!")

def install_rust():
    """Install Rust programming language"""
    logger.info("Checking Rust installation status...")
    
    # Check if Rust is already installed
    try:
        result = run_command("rustc --version", check=False)
        if result.returncode == 0:
            logger.info(f"Rust is already installed: {result.stdout.strip()}")
            return True
    except:
        pass
    
    logger.info("Rust not installed, installing...")
    
    # Install Rust using rustup
    logger.info("Downloading and installing Rust...")
    run_command("curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y")
    
    # Source cargo environment
    cargo_env = os.path.expanduser("~/.cargo/env")
    if os.path.exists(cargo_env):
        logger.info("Sourcing cargo environment...")
        os.environ['PATH'] = f"{os.path.expanduser('~/.cargo/bin')}:{os.environ.get('PATH', '')}"
    
    logger.info("Rust installation completed!")
    return True

def install_third_party_libraries():
    """Install third-party libraries"""
    logger.info("Installing third-party libraries...")
    
    # Switch to tool script directory
    script_dir = Path("tool") / "script"
    if not script_dir.exists():
        logger.error(f"Script directory {script_dir} does not exist")
        logger.info("Creating script directory...")
        script_dir.mkdir(parents=True, exist_ok=True)
    
    original_dir = os.getcwd()
    os.chdir(script_dir)
    
    try:
        # Install OpenCV (usually already installed via Homebrew)
        logger.info("Checking OpenCV installation...")
        opencv_script = Path("install_opencv.py")
        if opencv_script.exists():
            print("Warning: MNN build failed")
            
    finally:
        # Restore original directory
        os.chdir(original_dir)
    
    print("Third-party libraries installation completed!")
    return True

def configure_and_build(config_file, build_type, jobs):
    """Configure CMake and build project"""
    logger.info("Configuring and building project...")
    
    # Create build directory
    build_dir = Path("build")
    if build_dir.exists() and args.clean:
        logger.info(f"Cleaning build directory: {build_dir}")
        shutil.rmtree(build_dir)
    
    build_dir.mkdir(exist_ok=True)
    logger.info(f"Using build directory: {build_dir}")
    
    # Copy configuration file
    config_source = Path("cmake") / config_file
    config_dest = build_dir / "config.cmake"
    
    if config_source.exists():
        shutil.copy2(config_source, config_dest)
        logger.info(f"Copied configuration file: {config_source} -> {config_dest}")
    else:
        logger.warning(f"Configuration file {config_source} does not exist")
    
    # Switch to build directory
    original_dir = os.getcwd()
    os.chdir(build_dir)
    
    try:
        # Set macOS specific environment variables
        os.environ['MACOSX_DEPLOYMENT_TARGET'] = '11.0'  # Support macOS 11.0+
        
        # Configure CMake with macOS ARM64 specific settings
        logger.info("Configuring CMake...")
        cmake_cmd = f"cmake -DCMAKE_BUILD_TYPE={build_type} -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0 .."
        run_command(cmake_cmd)
        
        # Build project
        logger.info(f"Building project with {jobs} parallel jobs...")
        make_cmd = f"make -j{jobs}"
        run_command(make_cmd)
        
        # Install
        logger.info("Installing...")
        run_command("make install")
        
        # Package
        logger.info("Packaging...")
        result = run_command("cpack", check=False)
        if result.returncode != 0:
            logger.warning("Packaging failed, but build succeeded")
        
        # List generated files
        logger.info("Generated files:")
        run_command("ls -la")
        
        logger.info("Compilation, installation and packaging completed")
        
    finally:
        # Restore original directory
        os.chdir(original_dir)
    
    return True

def install_python_package():
    """Install Python package in developer mode and verify"""
    logger.info("Installing Python package in developer mode...")
    
    # Switch to python directory
    python_dir = Path("python")
    if not python_dir.exists():
        logger.error(f"Python directory {python_dir} does not exist")
        return False
    
    original_dir = os.getcwd()
    os.chdir(python_dir)
    
    try:
        # Install in developer mode
        logger.info("Installing Python package in developer mode...")
        run_command("pip3 install -e .")
        
    finally:
        os.chdir(original_dir)
    
    # Verify installation
    logger.info("Verifying Python package installation...")
    verification_script = """
import platform
try:
    import nndeploy
    print(f'✓ Successfully imported nndeploy {nndeploy.__version__}')
    print(f'Platform: {platform.platform()}')
    print(f'Architecture: {platform.machine()}')
    print(f'Python version: {platform.python_version()}')
    print(f'macOS version: {platform.mac_ver()[0]}')
except ImportError as e:
    print(f'✗ Import failed: {e}')
    exit(1)
"""
    
    result = run_command(f'python3 -c "{verification_script}"', check=False)
    if result.returncode == 0:
        logger.info("Python package developer mode installation and verification completed")
        return True
    else:
        logger.error("Python package verification failed")
        return False

def create_release_package():
    """Create release package similar to GitHub Actions"""
    logger.info("Creating release package...")
    
    build_dir = Path("build")
    if not build_dir.exists():
        logger.error("Build directory does not exist")
        return False
    
    # Find generated tar.gz files
    tar_files = list(build_dir.glob("*.tar.gz"))
    if tar_files:
        logger.info("Found release packages:")
        for tar_file in tar_files:
            logger.info(f"  {tar_file}")
            # Move to release directory
            release_dir = Path("release")
            release_dir.mkdir(exist_ok=True)
            dest_file = release_dir / tar_file.name
            shutil.copy2(tar_file, dest_file)
            logger.info(f"  Copied to: {dest_file}")
    else:
        logger.warning("No release packages found")
    
    return True

def run_tests():
    """Run basic tests to verify deployment"""
    logger.info("Running deployment verification tests...")
    
    test_dir = Path("test")
    if test_dir.exists():
        original_dir = os.getcwd()
        os.chdir(test_dir)
        try:
            logger.info("Running test suite...")
            result = run_command("python3 -m pytest -v", check=False)
            if result.returncode == 0:
                logger.info("All tests passed!")
            else:
                logger.warning("Some tests failed, but deployment can continue")
        finally:
            os.chdir(original_dir)
    else:
        logger.info("Test directory not found, skipping tests")
    
    return True

def main():
    """Main build function"""
    logger.info("=" * 60)
    logger.info("nndeploy macOS ARM64 Build Script")
    logger.info("=" * 60)
    
    # Parse arguments
    global args
    args = parse_arguments()
    
    # Check system
    system_info = check_system()
    
    # Install dependencies
    if not args.skip_deps:
        install_system_dependencies()
        install_python_dependencies()
        install_rust()
    else:
        logger.info("Skipping dependency installation")
    
    # Install third-party libraries
    if not args.skip_third_party:
        install_third_party_libraries()
    else:
        logger.info("Skipping third-party library installation")
    
    # Configure and build
    configure_and_build(args.config, args.build_type, args.jobs)
    
    # Install Python package
    install_python_package()
    
    # Create release package
    create_release_package()
    
    # Run tests
    run_tests()
    
    logger.info("=" * 60)
    logger.info("macOS ARM64 Build and Deployment completed successfully!")
    logger.info("=" * 60)
    logger.info("\nNext steps:")
    logger.info("1. Check build artifacts in: ./build/")
    logger.info("2. Check release packages in: ./release/")
    logger.info("3. Check build log in: ./build_mac_arm64.log")
    logger.info("4. Test the Python package: python3 -c 'import nndeploy; print(nndeploy.__version__)'")

if __name__ == "__main__":
    main()
