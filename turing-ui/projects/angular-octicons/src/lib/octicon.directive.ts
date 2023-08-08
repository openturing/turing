import { Directive, OnInit, ElementRef, Renderer2, Input } from '@angular/core';
import * as octicons from '@primer/octicons';
import { IconName } from '@primer/octicons';

@Directive({
  selector: '[octicon]'
})
export class OcticonDirective implements OnInit {

  @Input('octicon')
  iconName!: IconName;
  @Input()
  color!: string;
  @Input()
  size!: number;

  constructor(
    private elem: ElementRef,
    private renderer: Renderer2
  ) { }

  ngOnInit() {
    const el: HTMLElement = this.elem.nativeElement;
    el.innerHTML = octicons[this.iconName].toSVG();

    if (this.color) {
      this.renderer.setStyle(el.firstChild, 'fill', this.color);
    }

    if (this.size) {
      this.renderer.setStyle(el.firstChild, 'width', `${this.size}px`);
      this.renderer.setStyle(el.firstChild, 'height', '100%');
    }
  }


}
