import { Component, Input } from '@angular/core';

@Component({
  selector: 'shio-logo',
  template: `<svg class="mr-1" [style.width.px]="size" [style.height.px]="size" viewBox="0 0 549 549">
  <defs>
    <style>
      .cls-1 {
        fill: royalblue;
        stroke: #ffc;
        stroke-width: 20px;
        opacity: 1.0;
      }

      .cls-2 {
        font-size: 98.505px;
      }

      .cls-2, .cls-3 {
        fill: #ffc;
        font-family: "Proxima Nova";
        font-weight: 500;
      }

      .cls-3 {
        font-size: 25.538px;
      }
    </style>
  </defs>
  <rect class="cls-1" x="0.063" width="548" height="548.188" rx="100" ry="100"/>
  <text id="Tu" class="cls-2" transform="translate(64.825 442.418) scale(2.74 2.741)">Tu</text>
</svg>`,
})
export class ShioLogoComponent {
  @Input()
  size!: number;
}
