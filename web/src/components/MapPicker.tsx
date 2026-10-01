import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

export interface MapPoint {
  id: string;
  label: string;
  latitude: number;
  longitude: number;
  kind: "HUB" | "STORAGE" | "HOSPITAL";
}

const COLOURS: Record<MapPoint["kind"], string> = { HUB: "#ef3e2b", STORAGE: "#b82415", HOSPITAL: "#161616" };
const INDIA: [number, number] = [21.0, 78.9];

/**
 * An OpenStreetMap map. Shows the network's facilities; when {@code onPick} is given, clicking the map chooses a
 * location, shown as a large red marker.
 */
export function MapPicker({
  points,
  picked,
  onPick,
  height = 360,
}: {
  points: MapPoint[];
  picked?: { latitude: number; longitude: number } | null;
  onPick?: (latitude: number, longitude: number) => void;
  height?: number;
}) {
  const element = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const marker = useRef<L.CircleMarker | null>(null);
  const pick = useRef(onPick);
  pick.current = onPick;

  useEffect(() => {
    const m = L.map(element.current!, { scrollWheelZoom: false }).setView(INDIA, 5);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(m);
    m.on("click", (e: L.LeafletMouseEvent) => pick.current?.(round(e.latlng.lat), round(e.latlng.lng)));
    layer.current = L.layerGroup().addTo(m);
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
    };
  }, []);

  useEffect(() => {
    const m = map.current;
    const group = layer.current;
    if (!m || !group) return;
    group.clearLayers();
    for (const p of points) {
      L.circleMarker([p.latitude, p.longitude], {
        radius: p.kind === "HOSPITAL" ? 6 : 8,
        color: "#fff",
        weight: 2,
        fillColor: COLOURS[p.kind],
        fillOpacity: 1,
      })
        .bindTooltip(`${p.id} · ${p.label}`)
        .addTo(group);
    }
    if (!picked && points.length > 0) {
      m.fitBounds(L.latLngBounds(points.map((p) => [p.latitude, p.longitude])), { padding: [30, 30], maxZoom: 12 });
    }
  }, [points, picked]);

  useEffect(() => {
    const m = map.current;
    if (!m) return;
    marker.current?.remove();
    marker.current = null;
    if (picked) {
      marker.current = L.circleMarker([picked.latitude, picked.longitude], {
        radius: 11,
        color: "#fff",
        weight: 3,
        fillColor: "#ef3e2b",
        fillOpacity: 1,
      }).addTo(m);
      if (!m.getBounds().contains([picked.latitude, picked.longitude])) {
        m.setView([picked.latitude, picked.longitude], Math.max(m.getZoom(), 11));
      }
    }
  }, [picked]);

  return (
    <div
      ref={element}
      className="map"
      style={{ height }}
      role="application"
      aria-label={onPick ? "Map: click to choose the location" : "Map of the network"}
    />
  );
}

function round(n: number): number {
  return Math.round(n * 1e6) / 1e6;
}
