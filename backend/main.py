"""
AutoCall Mail Recorder - Companion FastAPI Backend Service
Provides authenticated file uploads, background SMTP delivery, and diagnostic health checks.
"""

import os
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.base import MIMEBase
from email.mime.text import MIMEText
from email import encoders
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Header, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(
    title="AutoCall Mail Recorder Delivery Backend",
    version="1.0.0",
    description="Secure delivery companion service for AutoCall Mail Recorder"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

API_KEY = os.getenv("DELIVERY_API_KEY", "autocall_secure_token_2026")
SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASS = os.getenv("SMTP_PASS", "")

def send_email_background(recipient: str, subject: str, body: str, file_bytes: bytes, filename: str):
    try:
        msg = MIMEMultipart()
        msg["From"] = SMTP_USER
        msg["To"] = recipient
        msg["Subject"] = subject

        msg.attach(MIMEText(body, "plain"))

        part = MIMEBase("application", "octet-stream")
        part.set_payload(file_bytes)
        encoders.encode_base64(part)
        part.add_header("Content-Disposition", f"attachment; filename={filename}")
        msg.attach(part)

        with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
            server.starttls()
            if SMTP_USER and SMTP_PASS:
                server.login(SMTP_USER, SMTP_PASS)
            server.send_message(msg)
    except Exception as e:
        print(f"[ERROR] Failed to send background email: {e}")

@app.get("/")
def health_check():
    return {
        "status": "healthy",
        "service": "AutoCall Mail Recorder Delivery API",
        "version": "1.0.0"
    }

@app.post("/api/v1/deliver")
async def deliver_recording(
    background_tasks: BackgroundTasks,
    recipient_email: str = Form(...),
    direction: str = Form(...),
    duration_seconds: int = Form(...),
    timestamp: str = Form(...),
    recording_file: UploadFile = File(...),
    x_api_key: str = Header(None)
):
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid API Key")

    file_contents = await recording_file.read()
    filename = recording_file.filename or "call_recording.m4a"

    subject = f"Call Recording – {direction} – {timestamp}"
    body = f"""
    AutoCall Mail Recorder
    ========================
    Call Direction: {direction}
    Timestamp: {timestamp}
    Duration: {duration_seconds // 60}m {duration_seconds % 60}s
    File Name: {filename}
    File Size: {len(file_contents) // 1024} KB
    
    Sent via AutoCall Secure Backend Service.
    """

    background_tasks.add_task(
        send_email_background,
        recipient=recipient_email,
        subject=subject,
        body=body,
        file_bytes=file_contents,
        filename=filename
    )

    return {
        "status": "QUEUED",
        "message": "Recording received and queued for email delivery.",
        "recipient": recipient_email,
        "size_bytes": len(file_contents)
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
