from ultralytics import YOLO
from collections import defaultdict
from math import sqrt
from itertools import combinations

class CardDetector:

    def __init__(self,
                 model_path="/workspace/models/selected/best.pt",
                 confidence=0.1):

        self.model = YOLO(model_path)
        self.confidence = confidence
        

    # detects the img at image_path
    # beyond than the class detected, a detection consists of:
    # condidence, bbox, center, width, height, area, ceneter_normalized, area_normalized
    def detect(self, image_path):
    
        results = self.model.predict(
            source=image_path,
            conf=self.confidence,
            verbose=False
        )
    
        detections = []
    
        for result in results:
    
            names = result.names
            img_h, img_w = result.orig_shape
            img_area = img_w * img_h

            i = 0
            for box in result.boxes:
    
                cls_id = int(box.cls.item())
                conf = float(box.conf.item())
    
                x1, y1, x2, y2 = box.xyxy[0].tolist()
    
                width = x2 - x1
                height = y2 - y1
                area = width * height
    
                center_x = (x1 + x2) / 2
                center_y = (y1 + y2) / 2
    
                detection = {
                    "class": names[cls_id],
                    "confidence": conf,
    
                    "bbox": (x1, y1, x2, y2),
    
                    "center": (
                        center_x,
                        center_y
                    ),
    
                    "center_normalized": (
                        center_x / img_w,
                        center_y / img_h
                    ),
    
                    "width": width,
                    "height": height,
    
                    "area": area,
    
                    "area_normalized": area / img_area
                }

                i += 1
                # print(f"Detection {i}: {detection}")
                
                detections.append(detection)
    
        return [det["class"] for det in self.distinct_cards_pairwise(detections)]

    
    # uses the distinction metric and data in detections to spot same card
    # (normalized area and center are used)
    def distinct_cards_pairwise(
        self,
        detections,
        threshold_min=4.5,
        threshold_max=6.7
    ):
    
        by_class = defaultdict(list)
    
        for det in detections:
            by_class[det["class"]].append(det)
    
        distinct = []
    
        for cls, dets in by_class.items():
    
            dets = sorted(
                dets,
                key=lambda d: d["confidence"],
                reverse=True
            )
    
            accepted = []
    
            for det in dets:
    
                duplicate = False
    
                cx1, cy1 = det["center_normalized"]
                a1 = det["area_normalized"]
    
                for other in accepted:
    
                    cx2, cy2 = other["center_normalized"]
                    a2 = other["area_normalized"]
    
                    distance = sqrt(
                        (cx1 - cx2) ** 2 +
                        (cy1 - cy2) ** 2
                    )

                    # DoA metric to spot same cards
                    metric = distance / sqrt((a1 + a2) / 2)

                    print(f"metric: {metric}")
    
                    if threshold_min <= metric <= threshold_max:
                        duplicate = True
                        break
    
                if not duplicate:
                    accepted.append(det)
    
            distinct.extend(accepted)
    
        return distinct
        
        