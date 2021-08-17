import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-shio-content-page',
  templateUrl: './shio-content-page.component.html'
})
export class ShioContentPageComponent implements OnInit {

  constructor(private router: Router) { }

  ngOnInit(): void {
  }
  getRouter(): Router {
    return this.router;
  }
  imports: [
  ]
}
