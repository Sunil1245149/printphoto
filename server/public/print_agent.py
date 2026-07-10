
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
    DOWNLOAD_DIR = "downloads"

    if not os.path.exists(DOWNLOAD_DIR):
        os.makedirs(DOWNLOAD_DIR)

    sio = socketio.Client(reconnection=True, reconnection_attempts=0, reconnection_delay=5)

    def print_banner():
        os.system('cls' if os.name == 'nt' else 'clear')
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
        print("\n❌ Disconnected from Server. Retrying... (सर्वर से संपर्क टूट गया है...)")

    @sio.on('print_job')
    def on_print_job(data):
        url = data.get('url')
        if not url: return
        
        image_url = url if url.startswith('http') else f"{sio.connection_url}{url}"
        
        print(f"\n⚡ New Print Job Received! (नया प्रिंट जॉब मिला है!)")
        print(f"📄 URL: {image_url}")
        
        file_path = os.path.join(DOWNLOAD_DIR, f"auto_print_{int(time.time())}.png")
        
        try:
            response = requests.get(image_url, timeout=15)
            if response.status_code == 200:
                with open(file_path, 'wb') as f:
                    f.write(response.content)
                
                print(f"✅ Downloaded successfully. Sending to printer...")
                if os.name == 'nt': # Windows
                    os.startfile(file_path, "print")
                else: # Linux/Mac
                    subprocess.run(["lp", file_path])
            else:
                print(f"❌ Failed to download: HTTP {response.status_code}")
        except Exception as e:
            print(f"❌ Error during printing: {e}")

    print_banner()
    print(f"Default URL: {DEFAULT_URL}")
    server_url = input(f"Enter Portal URL (Press Enter for default): ").strip() or DEFAULT_URL
    
    if not server_url.startswith('http'):
        server_url = 'https://' + server_url
    if server_url.endswith('/'):
        server_url = server_url[:-1]

    try:
        print(f"\nAttempting to connect to {server_url}...")
        sio.connect(server_url)
        sio.wait()
    except KeyboardInterrupt:
        print("\n\n👋 Agent stopped by user.")
    except Exception as e:
        print(f"\n❌ Critical Error: {e}")
        input("\nPress Enter to close and see the error...")

if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        print(f"\nFATAL CRASH: {e}")
        input("\nScript crashed. Press Enter to exit...")
