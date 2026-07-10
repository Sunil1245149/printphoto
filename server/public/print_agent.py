
try:
    import socketio
    import requests
except ImportError:
    print("\n[ERROR] Missing required libraries!")
    print("Please run this command first:")
    print("pip install \"python-socketio[client]\" requests")
    input("\nPress Enter to exit...")
    exit(1)

import os
import subprocess
import time

# Configuration
DEFAULT_URL = "https://ais-pre-cqh5itcyojkioxx6udfd4o-1072374194741.asia-southeast1.run.app"
print(f"Default URL: {DEFAULT_URL}")
SERVER_URL = input(f"Enter Portal URL (Press Enter for default): ") or DEFAULT_URL
DOWNLOAD_DIR = "downloads"

if SERVER_URL.endswith('/'):
    SERVER_URL = SERVER_URL[:-1]

if not os.path.exists(DOWNLOAD_DIR):
    os.makedirs(DOWNLOAD_DIR)

sio = socketio.Client()

@sio.event
def connect():
    print("Connected to Merchant Portal Server")

@sio.event
def disconnect():
    print("Disconnected from Server")

@sio.on('print_job')
def on_print_job(data):
    url = data.get('url')
    if not url: return
    
    # Check if absolute or relative
    if url.startswith('/'):
        image_url = f"{SERVER_URL}{url}"
    else:
        image_url = url
        
    print(f"New print job: {image_url}")
    file_path = os.path.join(DOWNLOAD_DIR, f"auto_print_{int(time.time())}.png")
    
    try:
        response = requests.get(image_url)
        if response.status_code == 200:
            with open(file_path, 'wb') as f:
                f.write(response.content)
            print(f"Saved to {file_path}")
            
            print(f"Sending {file_path} to printer...")
            if os.name == 'nt': # Windows
                os.startfile(file_path, "print")
            else: # Linux/Mac
                subprocess.run(["lp", file_path])
        else:
            print(f"Failed to download image: {response.status_code}")
    except Exception as e:
        print(f"Error during printing: {e}")

@sio.on('job-completed')
def on_job_completed(data):
    print(f"Job completed notification: {data['id']}")
    # Optional: we can trigger print here too if desired, but 'print_job' is more direct

def start_agent():
    while True:
        try:
            print(f"Connecting to {SERVER_URL}...")
            sio.connect(SERVER_URL)
            sio.wait()
        except Exception as e:
            print(f"Connection error or lost: {e}")
            print("Retrying in 10 seconds...")
            time.sleep(10)

if __name__ == "__main__":
    print("--- PassportPrint Pro Auto-Print Agent ---")
    print(f"Monitoring: {SERVER_URL}")
    try:
        start_agent()
    except KeyboardInterrupt:
        print("\nAgent stopped by user.")
    except Exception as e:
        print(f"Critical error: {e}")
    finally:
        input("\nAgent finished. Press Enter to close...")
