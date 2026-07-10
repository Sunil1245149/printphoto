
import sys
import os
import time
import subprocess

def install_dependencies():
    print("Checking dependencies... (Libraries check kar rahe hain...)")
    try:
        import socketio
        import requests
        return True
    except ImportError:
        print("\n" + "="*50)
        print("❌ [ERROR] Missing required libraries!")
        print("Installing automatically... (Apne aap install ho raha hai...)")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "python-socketio[client]", "requests"])
            print("✅ Installation complete! Please restart the script.")
            return True
        except Exception as e:
            print(f"❌ Auto-installation failed: {e}")
            print("\nManually run this command (Manually ye command chalayein):")
            print(f"{sys.executable} -m pip install \"python-socketio[client]\" requests")
            return False

def main():
    if not install_dependencies():
        input("\nPress Enter to exit...")
        return

    import socketio
    import requests

    # Current Session URL
    DEFAULT_URL = "https://ais-pre-cqh5itcyojkioxx6udfd4o-1072374194741.asia-southeast1.run.app"
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    DOWNLOAD_DIR = os.path.join(script_dir, "print_jobs")

    try:
        if not os.path.exists(DOWNLOAD_DIR):
            os.makedirs(DOWNLOAD_DIR)
    except Exception:
        DOWNLOAD_DIR = script_dir

    # Using standard logger for socketio to see more details if it fails
    sio = socketio.Client(
        reconnection=True, 
        reconnection_attempts=5, 
        reconnection_delay=2,
        logger=False, 
        engineio_logger=False
    )

    def print_banner():
        try:
            os.system('cls' if os.name == 'nt' else 'clear')
        except:
            pass
        print("="*60)
        print("🖨️  EASY-PRINT LOCAL PRINT AGENT (ऑटो-प्रिंट एजेंट)")
        print("="*60)

    @sio.event
    def connect():
        print(f"🔗 Connected to: {sio.connection_url}")
        print("📡 Waiting for incoming print jobs... (प्रिंट जॉब्स की प्रतीक्षा कर रहा है...)")
        print("Press Ctrl+C to exit.")
        print("="*60)

    @sio.event
    def disconnect():
        print("\n❌ Disconnected from Server. Retrying...")

    @sio.on('print_job')
    def on_print_job(data):
        url = data.get('url')
        if not url: return
        
        # Build absolute URL
        conn_url = sio.connection_url.rstrip('/')
        image_url = url if url.startswith('http') else f"{conn_url}{url}"
        
        print(f"\n⚡ New Print Job Received!")
        print(f"📄 Downloading: {image_url}")
        
        file_path = os.path.join(DOWNLOAD_DIR, f"print_{int(time.time())}.png")
        
        try:
            response = requests.get(image_url, timeout=20)
            if response.status_code == 200:
                with open(file_path, 'wb') as f:
                    f.write(response.content)
                
                print(f"✅ Saved to: {file_path}")
                print(f"🖨️  Sending to printer...")
                
                if os.name == 'nt': # Windows
                    os.startfile(file_path, "print")
                else: # Linux/Mac
                    subprocess.run(["lp", file_path])
            else:
                print(f"❌ Failed to download: HTTP {response.status_code}")
        except Exception as e:
            print(f"❌ Error during printing: {e}")

    print_banner()
    print(f"Current Portal: {DEFAULT_URL}")
    server_url = input(f"Enter Portal URL (Press Enter to use default): ").strip() or DEFAULT_URL
    
    if not server_url.startswith('http'):
        server_url = 'https://' + server_url
    if server_url.endswith('/'):
        server_url = server_url[:-1]

    try:
        # Step 1: Check if URL is reachable via HTTP first
        print(f"\nStep 1: Checking server availability...")
        ping_res = requests.get(f"{server_url}/ping", timeout=10)
        if ping_res.status_code == 200:
            print("✅ Server is online.")
        else:
            print(f"⚠️  Server responded with status {ping_res.status_code}. Attempting Socket.IO anyway...")
            
        # Step 2: Attempt Socket.IO connection
        print(f"Step 2: Connecting to Socket.IO...")
        # We try with multiple transports to avoid 404 proxy issues
        sio.connect(server_url, transports=['websocket', 'polling'], socketio_path='/socket.io/')
        sio.wait()
    except KeyboardInterrupt:
        print("\n\n👋 Agent stopped.")
    except Exception as e:
        print(f"\n❌ Connection Error: {e}")
        print("\nTip: Make sure you copied the correct 'Shared App URL' from AI Studio.")
        input("\nPress Enter to try again...")
        main()

if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        print(f"\nFATAL ERROR: {e}")
        input("\nPress Enter to exit...")
