
import socketio
import requests
import os
import subprocess
import time

# Configuration
SERVER_URL = "https://ais-pre-cqh5itcyojkioxx6udfd4o-1072374194741.asia-southeast1.run.app" # Update with your Render URL
DOWNLOAD_DIR = "downloads"

if not os.path.exists(DOWNLOAD_DIR):
    os.makedirs(DOWNLOAD_DIR)

sio = socketio.Client()

@sio.event
def connect():
    print("Connected to Merchant Portal Server")

@sio.event
def disconnect():
    print("Disconnected from Server")

@sio.on('job-completed')
def on_job_completed(data):
    print(f"New job completed: {data['id']}")
    image_url = f"{SERVER_URL}{data['preview']}"
    file_path = os.path.join(DOWNLOAD_DIR, f"print_{data['id']}.png")
    
    # Download the image
    print(f"Downloading {image_url}...")
    try:
        response = requests.get(image_url)
        if response.status_code == 200:
            with open(file_path, 'wb') as f:
                f.write(response.content)
            print(f"Saved to {file_path}")
            
            # Auto Print (Windows command example)
            # You might need to install 'Ghostscript' or use 'mspaint /p'
            print(f"Sending {file_path} to printer...")
            if os.name == 'nt': # Windows
                os.startfile(file_path, "print")
            else: # Linux/Mac
                subprocess.run(["lp", file_path])
        else:
            print(f"Failed to download image: {response.status_code}")
    except Exception as e:
        print(f"Error during printing: {e}")

def start_agent():
    try:
        sio.connect(SERVER_URL)
        sio.wait()
    except Exception as e:
        print(f"Connection failed: {e}")
        time.sleep(5)
        start_agent()

if __name__ == "__main__":
    print("--- PassportPrint Pro Auto-Print Agent ---")
    print(f"Monitoring: {SERVER_URL}")
    start_agent()
