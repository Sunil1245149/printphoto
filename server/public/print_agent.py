
import sys
import os
import time
import subprocess

def install_dependencies():
    print("\n" + "="*50)
    print("Checking dependencies... (Libraries check kar rahe hain...)")
    try:
        import socketio
        import requests
        return True
    except ImportError:
        print("❌ [ERROR] Missing required libraries!")
        print("Installing automatically... (Apne aap install ho raha hai...)")
        try:
            # Try both pip and pip3
            for pip_cmd in ["pip", "pip3"]:
                try:
                    subprocess.check_call([sys.executable, "-m", pip_cmd, "install", "python-socketio[client]", "requests"])
                    print("✅ Installation complete!")
                    return True
                except:
                    continue
            return False
        except Exception as e:
            print(f"❌ Auto-installation failed: {e}")
            return False

def main():
    if not install_dependencies():
        print("\nManually run: pip install \"python-socketio[client]\" requests")
        input("\nPress Enter to exit...")
        return

    import socketio
    import requests

    # ---------------------------------------------------------
    # CONFIGURATION
    # ---------------------------------------------------------
    # Note: Replace this with your actual Render URL if you have one
    DEFAULT_URL = "https://printphoto.onrender.com"
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    DOWNLOAD_DIR = os.path.join(script_dir, "print_jobs")

    try:
        if not os.path.exists(DOWNLOAD_DIR):
            os.makedirs(DOWNLOAD_DIR)
    except Exception:
        DOWNLOAD_DIR = script_dir

    sio = socketio.Client(
        reconnection=True, 
        reconnection_attempts=0, # Infinite retries
        reconnection_delay=5,
        logger=False, 
        engineio_logger=False
    )

    def print_banner():
        try:
            os.system('cls' if os.name == 'nt' else 'clear')
        except:
            pass
        print("="*65)
        print("🖨️  EASY-PRINT LOCAL PRINT AGENT (ऑटो-प्रिंट एजेंट)")
        print("="*65)
        print("हिन्दी निर्देश: यह प्रोग्राम आपके कंप्यूटर को प्रिंटर से जोड़ता है।")
        print("अगर आप AI Studio का URL डाल रहे हैं और 404 आ रहा है, ")
        print("तो इसका मतलब है कि सर्वर वहां नहीं चल रहा।")
        print("अपना Render.com वाला URL इस्तेमाल करें।")
        print("="*65)

    @sio.event
    def connect():
        print(f"\n✅ Connected to Server: {sio.connection_url}")
        print("📡 Waiting for jobs... (प्रिंट जॉब्स की प्रतीक्षा कर रहा है...)")
        print("Press Ctrl+C to stop.")
        print("-" * 40)

    @sio.event
    def disconnect():
        print("\n❌ Connection lost. Retrying in 5 seconds...")

    @sio.on('print_job')
    def on_print_job(data):
        url = data.get('url')
        if not url: return
        
        # Build absolute URL correctly
        conn_url = sio.connection_url.rstrip('/')
        image_url = url if url.startswith('http') else f"{conn_url}{url}"
        
        print(f"\n[NEW JOB] Received: {image_url}")
        
        file_path = os.path.join(DOWNLOAD_DIR, f"print_{int(time.time())}.png")
        
        try:
            print("⏳ Downloading image...")
            response = requests.get(image_url, timeout=30)
            if response.status_code == 200:
                with open(file_path, 'wb') as f:
                    f.write(response.content)
                
                print(f"✅ Saved: {file_path}")
                abs_path = os.path.abspath(file_path)
                print(f"🖨️  Sending to default printer: {abs_path}")
                
                if os.name == 'nt': # Windows
                    print(f"🖨️  Starting PowerShell print (Direct)...")
                    # Use forward slashes for path to avoid PowerShell escaping issues
                    escaped_path = abs_path.replace('\\', '/')
                    ps_script = f"""
                    Add-Type -AssemblyName System.Drawing
                    $doc = New-Object System.Drawing.Printing.PrintDocument
                    $doc.DocumentName = "Passport Photo Print"
                    $doc.add_PrintPage({{
                        param($sender, $e)
                        try {{
                            $img = [System.Drawing.Image]::FromFile("{escaped_path}")
                            # Draw image to fill the printable area (MarginBounds)
                            $e.Graphics.DrawImage($img, $e.MarginBounds)
                            $img.Dispose()
                        }} catch {{
                            Write-Error "Error in PrintPage: $_"
                        }}
                    }})
                    $doc.Print()
                    """
                    result = subprocess.run(["powershell", "-Command", ps_script], capture_output=True, text=True)
                    if result.stderr:
                        print(f"❌ PowerShell Error: {result.stderr}")
                else: # Linux/Mac
                    subprocess.run(["lp", file_path])
            else:
                print(f"❌ Download failed: HTTP {response.status_code}")
        except Exception as e:
            print(f"❌ Printing Error: {e}")

    print_banner()
    print(f"Suggested URL: {DEFAULT_URL}")
    server_url = input(f"Enter Portal URL (Press Enter to use suggested): ").strip() or DEFAULT_URL
    
    if not server_url.startswith('http'):
        server_url = 'https://' + server_url
    if server_url.endswith('/'):
        server_url = server_url[:-1]

    try:
        print(f"\nChecking server: {server_url} ...")
        # Try a quick ping
        try:
            requests.get(f"{server_url}/ping", timeout=5)
        except:
            pass # Socket.IO might work even if /ping fails
            
        print(f"Connecting to Socket.IO...")
        sio.connect(server_url, transports=['polling', 'websocket'])
        sio.wait()
    except KeyboardInterrupt:
        print("\n\n👋 Stopped.")
    except Exception as e:
        print(f"\n❌ Error: {e}")
        print("\nSALAHA (Suggestion):")
        print("1. Copy correct URL from your Render Dashboard.")
        print("2. Make sure server is UP and running.")
        input("\nPress Enter to try again...")
        main()

if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        print(f"\nFATAL ERROR: {e}")
        input("\nPress Enter to exit...")
