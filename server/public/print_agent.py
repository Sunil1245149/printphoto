
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
    DEFAULT_URL = "https://printphoto.onrender.com"
    
    print_banner()
    print(f"Suggested URL: {DEFAULT_URL}")
    server_url = input(f"Enter Portal URL (Press Enter to use suggested): ").strip() or DEFAULT_URL
    
    if not server_url.startswith('http'):
        if 'localhost' in server_url or '127.0.0.1' in server_url:
            server_url = 'http://' + server_url
        else:
            server_url = 'https://' + server_url
    if server_url.endswith('/'):
        server_url = server_url[:-1]

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
        job_id = data.get('jobId')
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
                    $img = [System.Drawing.Image]::FromFile("{escaped_path}")
                    
                    # Use StandardPrintController to suppress the "Printing..." status dialog
                    $doc.PrintController = New-Object System.Drawing.Printing.StandardPrintController
                    
                    # Auto Orientation: Width > Height means 8 photos (Landscape), else 4 photos (Portrait)
                    if ($img.Width -gt $img.Height) {{
                        $doc.DefaultPageSettings.Landscape = $true
                    }} else {{
                        $doc.DefaultPageSettings.Landscape = $false
                    }}
                    
                    # Ensure Color mode is enabled
                    $doc.DefaultPageSettings.Color = $true
                    
                    # Set margins to 0 for full 4x6 printing (we handle margins in layout)
                    $doc.DefaultPageSettings.Margins = New-Object System.Drawing.Printing.Margins(0,0,0,0)
                    $doc.OriginAtMargins = $false
                    
                    $doc.add_PrintPage({{
                        param($sender, $e)
                        try {{
                            $graphics = $e.Graphics
                            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                            
                            # Use page area with a tiny safety margin (2% buffer) to avoid cutting
                            $pBounds = $e.PageBounds
                            $safeMarginX = $pBounds.Width * 0.02
                            $safeMarginY = $pBounds.Height * 0.02
                            
                            $rect = New-Object System.Drawing.RectangleF($safeMarginX, $safeMarginY, $pBounds.Width - ($safeMarginX * 2), $pBounds.Height - ($safeMarginY * 2))
                            
                            Write-Host "Printing to Area: $($rect.Width)x$($rect.Height) inside $($pBounds.Width)x$($pBounds.Height)"
                            
                            # Preserve Aspect Ratio to avoid "daba hua" (squashed) look
                            $imageRatio = $img.Width / $img.Height
                            $pageRatio = $rect.Width / $rect.Height
                            
                            $drawWidth = $rect.Width
                            $drawHeight = $rect.Height
                            $offsetX = $rect.X
                            $offsetY = $rect.Y
                            
                            if ($imageRatio -gt $pageRatio) {{
                                # Image is wider than page ratio
                                $drawHeight = $rect.Width / $imageRatio
                                $offsetY = $rect.Y + ($rect.Height - $drawHeight) / 2
                            }} else {{
                                # Image is taller than page ratio
                                $drawWidth = $rect.Height * $imageRatio
                                $offsetX = $rect.X + ($rect.Width - $drawWidth) / 2
                            }}
                            
                            # Draw image centered with high quality scaling
                            $graphics.DrawImage($img, [float]$offsetX, [float]$offsetY, [float]$drawWidth, [float]$drawHeight)
                            $e.HasMorePages = $false
                        }} catch {{
                            Write-Error "Error in PrintPage: $_"
                        }}
                    }})
                    $doc.Print()
                    $img.Dispose()
                    """
                    result = subprocess.run(["powershell", "-Command", ps_script], capture_output=True, text=True)
                    if result.stderr:
                        print(f"❌ PowerShell Error: {result.stderr}")
                    else:
                        print(f"✅ Print command sent successfully.")
                        # Notify server
                        try:
                            requests.post(f"{server_url}/api/print-success", json={"jobId": job_id})
                        except Exception as e:
                            print(f"⚠️  Could not notify server: {e}")
                else: # Linux/Mac
                    subprocess.run(["lp", file_path])
                    try:
                        requests.post(f"{server_url}/api/print-success", json={"jobId": job_id})
                    except Exception as e:
                        print(f"⚠️  Could not notify server: {e}")
            else:
                print(f"❌ Download failed: HTTP {response.status_code}")
        except Exception as e:
            print(f"❌ Printing Error: {e}")

    print_banner()
    
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
