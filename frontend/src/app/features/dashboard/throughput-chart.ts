import { Component, ElementRef, OnDestroy, afterNextRender, effect, input, viewChild } from '@angular/core';
import {
  CategoryScale, Chart, Filler, LineController, LineElement, LinearScale, PointElement, Tooltip,
} from 'chart.js';

// Register only what this chart uses so the bundle stays small.
Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Filler, Tooltip);

@Component({
  selector: 'ps-throughput-chart',
  template: `<div class="chart"><canvas #canvas aria-label="Payments initiated per second, last 60 seconds" role="img"></canvas></div>`,
  styles: `.chart { position: relative; height: 210px; }`,
})
export class ThroughputChart implements OnDestroy {
  readonly series = input<number[]>([]);

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart<'line'>;

  constructor() {
    afterNextRender(() => {
      const accent = '#10b981';
      const muted = '#8b8b94';
      this.chart = new Chart(this.canvas().nativeElement, {
        type: 'line',
        data: {
          labels: Array.from({ length: 60 }, (_, i) => `${i - 59}s`),
          datasets: [{
            data: this.series(),
            borderColor: accent,
            backgroundColor: 'rgba(16, 185, 129, 0.10)',
            fill: true,
            tension: 0.35,
            pointRadius: 0,
            borderWidth: 1.75,
          }],
        },
        options: {
          animation: false,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          plugins: { tooltip: { callbacks: { label: ctx => `${ctx.parsed.y} payments/s` } } },
          scales: {
            x: { ticks: { color: muted, maxTicksLimit: 7, font: { size: 11 } }, grid: { display: false }, border: { display: false } },
            y: { beginAtZero: true, ticks: { color: muted, precision: 0 }, grid: { color: 'rgba(148,163,184,0.12)' }, border: { display: false } },
          },
        },
      });
    });

    effect(() => {
      const data = this.series();
      if (this.chart) {
        this.chart.data.datasets[0].data = data;
        this.chart.update('none');
      }
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}
