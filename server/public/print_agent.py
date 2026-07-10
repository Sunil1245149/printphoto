
try:
    import socketio
    import requests
except ImportError:
    print("\n" + "="*50)
    print("❌ [ERROR] Missing required libraries!")
    print("Please run this command first (यह कमांड चलाएं):")
    print("pip install \"python-socketio[client]\" requests")
    print("="*50)
    input("\nPress Enter to exit...")
    exit(1)

import os
import time
import sys
import subprocess

# Configuration
# Note: AI Studio URL changes per session, this is the current one
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
        response = requests.get(image_url, timeout=10)
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

def start_agent():
    print_banner()
    print(f"Default URL: {DEFAULT_URL}")
    server_url = input(f"Enter Portal URL (Press Enter for default): ").strip() or DEFAULT_URL
    
    if not server_url.startswith('http'):
        server_url = 'https://' + server_url
    if server_url.endswith('/'):
        server_url = server_url[:-1]

    try:
        sio.connect(server_url)
        sio.wait()
    except KeyboardInterrupt:
        print("\n\n👋 Agent stopped by user. (एजेंट बंद कर दिया गया है।)")
        sys.exit(0)
    except Exception as e:
        print(f"\n❌ Connection Error: {e}")
        input("\nPress Enter to try again...")
        start_agent()

if __name__ == '__main__':
    start_agent()
