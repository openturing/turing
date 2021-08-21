import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'console-page',
  templateUrl: './console-page.component.html'
})
export class TurConsolePageComponent implements OnInit {

  constructor(private router: Router) { }

  ngOnInit(): void {
  }
  getRouter(): Router {
    return this.router;
  }
  imports: [
  ]
}
