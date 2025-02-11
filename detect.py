import os
import cv2
import torch
from flask import Flask, request, jsonify
from flask import send_from_directory
from werkzeug.utils import secure_filename
import time
import shutil

app = Flask(__name__)

MODEL_PATH = os.path.join("zHate.pt")
MODEL = torch.hub.load('ultralytics/yolov5', 'custom', path=MODEL_PATH, force_reload=True)

UPLOAD_FOLDER = 'uploads/'
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

CONFIDENCE_THRESHOLD = 0.9  # 인식 임계값
ALLOWED_EXTENSIONS = {'mp4', 'avi', 'mov', 'mkv'}

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

RESULT_FOLDER = 'result/'
os.makedirs(RESULT_FOLDER, exist_ok=True)  # 결과 폴더 생성

app.config['RESULT_FOLDER'] = RESULT_FOLDER

from twilio.rest import Client

# Twilio 계정 SID와 인증 토큰 설정
account_sid = 'AC30f5c5d662cd70ff0193a043b59bbb3e'
auth_token = '55b020e54ee316823fe66b1277b9ba96'
twilio_number = '+17083406710'  # Twilio에서 제공된 발신 번호
recipient_phone_number = '+821049117268'  # 문자 메시지를 받을 수신 번호

client = Client(account_sid, auth_token)

@app.route('/result/<filename>', methods=['GET'])
def download_result_file(filename):
    return send_from_directory(app.config['RESULT_FOLDER'], filename)

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

@app.route('/upload', methods=['POST'])
def upload_video():
    if 'video' not in request.files:
        return jsonify({'error': 'No file part'}), 400
    file = request.files['video']
    if file.filename == '' or not allowed_file(file.filename):
        return jsonify({'error': 'Invalid file'}), 400

    filename = secure_filename(file.filename)
    file_path = os.path.join(UPLOAD_FOLDER, filename)
    file.save(file_path)

    results = process_video(file_path)

    # 처리 후 영상 파일 삭제
    try:
        os.remove(file_path)
    except Exception as e:
        print(f"Error deleting file: {e}")

    return jsonify(results)

def process_video(video_path):
    cap = cv2.VideoCapture(video_path)
    detection_count = 0
    detected_frames = []
    detected_class_names = set()
    frame_count = 0
    video_id = int(time.time())  # 비디오 id
    video_folder = f"detected_video{video_id}"
    os.makedirs(video_folder, exist_ok=True)

    class_names = {0: "pistol", 1: "knife", 2: "ciga"}  # 클래스 이름

    last_frame_path = None
    last_detection_info_path = None

    while cap.isOpened() and detection_count < 3:
        ret, frame = cap.read()
        if not ret:
            break

        frame_count += 1

        results = MODEL(frame)
        detections = results.pandas().xyxy[0]

        frame_has_detection = False
        for _, detection in detections.iterrows():
            confidence = float(detection['confidence'])
            if confidence >= CONFIDENCE_THRESHOLD:
                class_id = int(detection['class'])
                if class_id in class_names:
                    frame_has_detection = True
                    detected_class_names.add(class_names[class_id])
                    print(f"Detected {class_names[class_id]} with confidence: {confidence:.2%}")

        if frame_has_detection:
            detection_count += 1
            frame_folder = f"{video_folder}/detected_video{video_id}_frames{detection_count}"
            os.makedirs(frame_folder, exist_ok=True)

            # 마지막으로 저장된 프레임 경로 업데이트
            last_frame_path = save_detected_frame(frame, frame_folder, class_names[class_id])
            detected_frames.append(last_frame_path)

            # 마지막으로 저장된 텍스트 파일 경로 업데이트
            last_detection_info_path = save_detection_info(video_folder, detection_count, class_names[class_id], confidence)

    cap.release()

    # 유해물이 3회 이상 감지된 경우
    if detection_count >= 3:
        print(f"Last Frame Path: {last_frame_path}")
        print(f"Last Detection Info Path: {last_detection_info_path}")
        
        # Twilio SMS 전송
        if last_frame_path and last_detection_info_path:
            print("Sending alert SMS...")
            send_alert_sms(last_frame_path, last_detection_info_path)
            print("Alert SMS sent.")
        else:
            print("Last frame path or detection info path is not available. Cannot send SMS.")

        return {
            'objectDetected': True,
            'message': f"{', '.join(detected_class_names)}가 탐지되었습니다",
            'detected_frames': detected_frames
        }
    else:
        # 동영상 URL 반환
        video_url = f"http://192.168.45.94/uploads/{os.path.basename(video_path)}"
        return {
            'objectDetected': False,
            'videoUrl': video_url,
            'message': "객체 감지를 완료했습니다."
        }



def save_detected_frame(frame, folder_path, class_name):
    output_filename = f"detected_{class_name}_frame.jpg"
    output_path = os.path.join(folder_path, output_filename)

    # 프레임을 지정된 폴더에 저장
    cv2.imwrite(output_path, frame)
    print(f"프레임 저장됨: {output_path}")

    # result 폴더 경로 설정
    result_output_path = os.path.join(app.config['RESULT_FOLDER'], output_filename)

    # result 폴더에 이전 이미지가 존재하면 삭제
    if os.path.exists(result_output_path):
        os.remove(result_output_path)  # 이전 이미지 삭제

    # 새로운 이미지 저장
    cv2.imwrite(result_output_path, frame)  # result 폴더에 프레임 저장
    print(f"복사된 프레임: {result_output_path}")
    
    return output_path

def save_detection_info(video_folder, detection_number, class_name, confidence):
    info_path = os.path.join(video_folder, f"detected_video{detection_number}_txt")
    with open(info_path, 'w', encoding='utf-8') as f:
        f.write(f"Frame {detection_number}개의 {class_name}가 탐지되었습니다. (인식률: {confidence:.2%})\n")
    
    print(f"Detection info saved: {info_path}")  # 로그 추가
    return info_path  # 경로 반환 추가


def send_alert_sms(detected_frame_path, detection_info_path):
    with open(detection_info_path, 'r', encoding='utf-8') as f:
        detection_info = f.read()
        print("Detection info read for SMS.")

    # 이미지 URL 생성
    image_url = generate_image_url(detected_frame_path)
    print(f"Generated image URL: {image_url}")

    # Twilio API를 이용하여 SMS 전송
    try:
        message = client.messages.create(
            body=f"고객님의 전송 영상에서 \n{detection_info}\n해당 영상의 이미지 확인: {image_url}",
            from_=twilio_number,
            to=recipient_phone_number
        )
        print(f"SMS sent with SID: {message.sid}")
    except Exception as e:
        print(f"Error sending SMS: {e}")

def generate_image_url(image_path):
    # result 폴더의 이미지 파일에 대한 URL 생성
    server_ip = "218.51.98.163"  # 서버의 IP 주소로 대체
    server_port = "8080"
    image_filename = os.path.basename(image_path)  # 파일 이름 추출
    image_url = f"http://{server_ip}:{server_port}/result/{image_filename}"  # result 경로로 변경
    return image_url

if __name__ == '__main__':
    app.run('0.0.0.0', port=8080, debug=True)
